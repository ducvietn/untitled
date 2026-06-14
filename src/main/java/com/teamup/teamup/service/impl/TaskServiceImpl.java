package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.event.TaskProgressUpdatedEvent;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaskService}.
 *
 * <h3>Event publishing</h3>
 * When task progress transitions to 100%, this service publishes a
 * {@link TaskProgressUpdatedEvent} after the transaction commits
 * (via {@code @TransactionEventListener(phase = AFTER_COMMIT)}).
 * The {@link com.teamup.teamup.event.NotificationEventListener} subscribes to this event
 * and creates an in-app notification for the group leader.
 *
 * <h3>Why AFTER_COMMIT?</h3>
 * If the transaction rolls back after a notification is saved, the notification
 * becomes a dangling record. By using {@code TransactionEventListener(AFTER_COMMIT)},
 * notifications are only persisted when the task update itself has been committed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository       taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Task updateProgress(Long taskId, int progress, User updater) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        int previousProgress = task.getProgress();
        int clamped         = Math.max(0, Math.min(100, progress));

        if (clamped == previousProgress) {
            log.debug("Progress unchanged ({}%) for task {}", clamped, taskId);
            return task;
        }

        task.setProgress(clamped);

        // Auto-transition to PENDING_REVIEW when hitting 100%
        if (clamped == 100 && task.getStatus() != TaskStatus.DONE) {
            task.setStatus(TaskStatus.PENDING_REVIEW);
            log.info("Task {} auto-transitioned to PENDING_REVIEW (progress → 100%)", taskId);
        }

        task = taskRepository.save(task);

        // ── Publish event if progress reached 100 % ────────────────────────────
        // TransactionEventListener ensures this fires only after the DB commit.
        if (previousProgress < 100 && clamped == 100) {
            log.info("Task {} completed by {}. Publishing TaskProgressUpdatedEvent.",
                taskId, updater.getId());
            eventPublisher.publishEvent(new TaskProgressUpdatedEvent(
                this, task, updater, previousProgress, clamped));
        }

        log.info("Task {} progress updated: {}% → {}% by user {}",
            taskId, previousProgress, clamped, updater.getId());

        return task;
    }
}
