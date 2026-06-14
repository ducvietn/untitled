package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.*;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.event.TaskProgressUpdatedEvent;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service 8 — Master Override Capability.
 *
 * Provides ROLE_TEACHER with absolute, unconditional authority over all groups
 * across the entire platform. These operations bypass all group-membership
 * and ownership checks that apply to regular students.
 *
 * <h3>Operations</h3>
 * <ol>
 *   <li><strong>Task modification</strong> — update deadline, reassign, delete any task.</li>
 *   <li><strong>Force-approve / force-reject</strong> — override any submission, regardless of leader decision.</li>
 *   <li><strong>Task unlock</strong> — force a DONE task back to IN_PROGRESS, clearing the lock.</li>
 *   <li><strong>Read all files</strong> — read-only access to all S3/Cloudinary URLs in any group.</li>
 * </ol>
 *
 * <h3>Audit trail</h3>
 * Every override action is logged with the teacher ID, target resource, old value,
 * and new value. In production this should write to an {@code AuditLog} entity.
 *
 * <h3>Security model</h3>
 * These methods are called ONLY from within controller methods that carry
 * {@code @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")}. The service layer
 * does NOT re-verify the caller's role — that is the sole responsibility of
 * the controller/security layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MasterOverrideService {

    private final TaskRepository       taskRepository;
    private final SubmissionRepository submissionRepository;
    private final GroupRepository      groupRepository;
    private final UserRepository       userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ══════════════════════════════════════════════════════════════════════════════
    // Task overrides
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Force-extends the deadline of a specific task.
     * Accessible to any teacher regardless of which class the task belongs to.
     *
     * @param taskId        the task to modify
     * @param newDeadline   the new deadline
     * @param teacherId     the teacher performing the override (for audit)
     * @return the updated task
     */
    public Task forceExtendDeadline(Long taskId, LocalDateTime newDeadline, Long teacherId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        LocalDateTime oldDeadline = task.getDeadline();
        task.setDeadline(newDeadline);
        task = taskRepository.save(task);

        logAudit("FORCE_EXTEND_DEADLINE",
            teacherId, taskId,
            "deadline: " + oldDeadline + " → " + newDeadline);

        return task;
    }

    /**
     * Reassigns a task to a different student.
     *
     * @param taskId       the task to reassign
     * @param newUserId    the new assignee's user ID
     * @param teacherId    the teacher performing the override
     * @return the updated task
     */
    public Task forceReassignTask(Long taskId, Long newUserId, Long teacherId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        User newAssignee = userRepository.findById(newUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", newUserId));

        Long   oldUserId  = task.getAssignedTo().getId();
        String oldUserName = task.getAssignedTo().getName();
        task.setAssignedTo(newAssignee);
        task = taskRepository.save(task);

        logAudit("FORCE_REASSIGN",
            teacherId, taskId,
            "assignee: " + oldUserName + " (id=" + oldUserId + ") → " + newAssignee.getName() + " (id=" + newUserId + ")");

        return task;
    }

    /**
     * Directly updates task fields: name, description, deadline, progress.
     * The teacher can set any progress value (0–100) and any status.
     *
     * @param taskId         the task to update
     * @param taskName       new name (null = no change)
     * @param description    new description (null = no change)
     * @param deadline       new deadline (null = no change)
     * @param progress       new progress 0–100 (null = no change)
     * @param status         new status (null = no change)
     * @param teacherId      the teacher performing the override
     * @return the updated task
     */
    public Task forceModifyTask(Long taskId, String taskName, String description,
                                 LocalDateTime deadline, Integer progress,
                                 TaskStatus status, Long teacherId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        StringBuilder changes = new StringBuilder();

        if (taskName != null && !taskName.equals(task.getTaskName())) {
            changes.append("taskName: ").append(task.getTaskName()).append(" → ").append(taskName).append("; ");
            task.setTaskName(taskName);
        }
        if (description != null) {
            task.setDescription(description);
        }
        if (deadline != null && !deadline.equals(task.getDeadline())) {
            changes.append("deadline: ").append(task.getDeadline()).append(" → ").append(deadline).append("; ");
            task.setDeadline(deadline);
        }
        int previousProgress = task.getProgress();
        int clamped         = Math.max(0, Math.min(100, progress));

        if (progress != null) {
            changes.append("progress: ").append(previousProgress).append(" → ").append(clamped).append("; ");
            task.setProgress(clamped);
        }
        if (status != null && status != task.getStatus()) {
            changes.append("status: ").append(task.getStatus()).append(" → ").append(status).append("; ");
            task.setStatus(status);
        }

        task = taskRepository.save(task);

        // Publish completion event if progress just hit 100 % (teacher override)
        if (previousProgress < 100 && clamped == 100) {
            User teacher = userRepository.findById(teacherId).orElse(null);
            if (teacher != null) {
                eventPublisher.publishEvent(new TaskProgressUpdatedEvent(
                    this, task, teacher, previousProgress, clamped));
                log.info("Task {} completed by teacher override. Event published.", taskId);
            }
        }

        if (!changes.isEmpty()) {
            logAudit("FORCE_MODIFY_TASK", teacherId, taskId, changes.toString());
        }

        return task;
    }

    /**
     * Deletes any task unconditionally. Students cannot delete tasks they own.
     *
     * @param taskId    the task to delete
     * @param teacherId the teacher performing the override
     */
    public void forceDeleteTask(Long taskId, Long teacherId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        taskRepository.deleteById(taskId);
        logAudit("FORCE_DELETE_TASK", teacherId, taskId, "task deleted");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Task unlock
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Unlocks a task locked at 100% DONE by forcing it back to IN_PROGRESS.
     * Use case: leader incorrectly locked the task; teacher corrects it.
     *
     * @param taskId    the task to unlock
     * @param teacherId the teacher performing the override
     * @return the updated task (status = IN_PROGRESS, progress reset to 0)
     */
    public Task forceUnlockTask(Long taskId, Long teacherId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        TaskStatus oldStatus = task.getStatus();
        Integer    oldProgress = task.getProgress();

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProgress(0);
        task = taskRepository.save(task);

        logAudit("FORCE_UNLOCK_TASK",
            teacherId, taskId,
            "status: " + oldStatus + " → IN_PROGRESS, progress: " + oldProgress + " → 0");

        return task;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Submission overrides (force-approve / force-reject)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Force-approves a submission — sets the task to DONE regardless of
     * who the current leader is or what the current task state is.
     *
     * @param submissionId the submission to approve
     * @param teacherId    the teacher performing the override
     * @return the updated task (status = DONE, progress = 100)
     */
    public Task forceApproveSubmission(Long submissionId, Long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));

        Task task = submission.getTask();
        TaskStatus oldStatus   = task.getStatus();
        Integer    oldProgress = task.getProgress();

        task.setStatus(TaskStatus.DONE);
        task.setProgress(100);
        task = taskRepository.save(task);

        // Publish completion event when teacher force-approves
        User teacher = userRepository.findById(teacherId).orElse(null);
        if (teacher != null) {
            eventPublisher.publishEvent(new TaskProgressUpdatedEvent(
                this, task, teacher, oldProgress, 100));
        }

        logAudit("FORCE_APPROVE",
            teacherId, task.getId(),
            "submission=" + submissionId + ", status: " + oldStatus + " → DONE, progress: " + oldProgress + " → 100");

        return task;
    }

    /**
     * Force-rejects a submission — reverts the task to IN_PROGRESS so the
     * student can resubmit.
     *
     * @param submissionId  the submission to reject
     * @param reason       optional reason string (stored in submission or audit)
     * @param teacherId    the teacher performing the override
     * @return the updated task (status = IN_PROGRESS, progress reset to last safe value)
     */
    public Task forceRejectSubmission(Long submissionId, String reason, Long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));

        Task task = submission.getTask();
        TaskStatus oldStatus   = task.getStatus();
        Integer    oldProgress = task.getProgress();

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProgress(50); // safe default — student must update
        task = taskRepository.save(task);

        logAudit("FORCE_REJECT",
            teacherId, task.getId(),
            "submission=" + submissionId + ", reason=" + reason +
            ", status: " + oldStatus + " → IN_PROGRESS, progress: " + oldProgress + " → 50");

        return task;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Read access (no modification)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns all submissions across all groups (read-only for the teacher dashboard).
     * The teacher can view ALL submitted files regardless of task status.
     *
     * @param groupId the group to list submissions for
     * @return all submissions for the group
     */
    @Transactional(readOnly = true)
    public List<Submission> getAllFilesInGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
        return submissionRepository.findAllByGroupId(groupId);
    }

    /**
     * Returns the group ID for a given task.
     * Used by TeacherController to resolve scope before deletion.
     *
     * @param taskId the task to look up
     * @return the group's primary key
     * @throws ResourceNotFoundException if the task does not exist
     */
    @Transactional(readOnly = true)
    public Long findGroupIdForTask(Long taskId) {
        return taskRepository.findGroupIdById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Audit helper
    // ══════════════════════════════════════════════════════════════════════════════

    private void logAudit(String action, Long teacherId, Long targetId, String details) {
        log.warn("[TEACHER OVERRIDE] action={}, teacherId={}, target={}, changes={}",
            action, teacherId, targetId, details);
        // TODO: persist to AuditLog entity:
        // auditLogRepository.save(AuditLog.builder()
        //     .action(action).actorId(teacherId).targetId(targetId)
        //     .details(details).timestamp(LocalDateTime.now()).build());
    }
}
