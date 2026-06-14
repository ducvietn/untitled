package com.teamup.teamup.event;

import com.teamup.teamup.entity.GroupObserver;
import com.teamup.teamup.entity.Notification;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.NotificationType;
import com.teamup.teamup.repository.GroupObserverRepository;
import com.teamup.teamup.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Central event listener for all in-app notification dispatches.
 *
 * <h3>Design goals</h3>
 * <ul>
 *   <li><strong>Decoupled</strong>: task services know nothing about notification persistence.</li>
 *   <li><strong>Transactional</strong>: notification writes share the caller's transaction
 *       (REQUIRED propagation). If the caller rolls back, notifications are also rolled back.</li>
 *   <li><strong>Async for high-volume events</strong>: frozen-task detection runs in a
 *       cron context — notifications are sent asynchronously to avoid holding the cron
 *       transaction open.</li>
 *   <li><strong>Idempotent-enough</strong>: listeners react to domain events; duplicate
 *       dispatches are prevented at the event-source level (e.g. only fire when
 *       progress transitions to 100, not on every save).</li>
 * </ul>
 *
 * <h3>Event → notification mapping</h3>
 * <table>
 *   <tr><th>Event</th><th>Recipients</th><th>Type</th></tr>
 *   <tr><td>TaskProgressUpdatedEvent (→ 100%)</td><td>Group leader</td><td>APP_ALERT</td></tr>
 *   <tr><td>TaskFrozenEvent</td><td>Assigned student + leader + all observers</td><td>SYSTEM_WARNING</td></tr>
 * </table>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationRepository   notificationRepository;
    private final GroupObserverRepository  groupObserverRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // TaskProgressUpdatedEvent — task just hit 100 %
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fires when a task's progress reaches 100%.
     * Notifies the group leader so they can review and approve the submission.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskProgressUpdatedEvent event) {
        if (event.getNewProgress() != 100) return;

        var task   = event.getTask();
        var leader = task.getGroup().getLeader();
        var group  = task.getGroup();

        String title   = "Task Completed";
        String message = String.format(
            "\"%s\" has been marked as 100%% by %s. " +
            "Please review and approve or request revisions.",
            task.getTaskName(),
            event.getUpdatedBy().getName());

        notificationRepository.save(Notification.builder()
            .user(leader)
            .title(title)
            .message(message)
            .type(NotificationType.APP_ALERT)
            .isRead(false)
            .build());

        log.info("Notification sent to leader {} for completed task {} in group {}",
            leader.getId(), task.getId(), group.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TaskFrozenEvent — cron detected a frozen task
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fires when a task has been stuck at the same progress for longer than
     * {@code 72 hours}. Notifies:
     * <ol>
     *   <li>The assigned student (the primary actor who needs to act)</li>
     *   <li>The group leader (for oversight)</li>
     *   <li>All active observers of the group (read-only watchers)</li>
     * </ol>
     *
     * Runs asynchronously because this is triggered from a cron scheduler
     * and should not block the transaction that detected the frozen task.
     */
    @Async
    @Transactional
    public void onTaskFrozen(TaskFrozenEvent event) {
        var task  = event.getTask();
        var group = task.getGroup();

        String title   = "Task Frozen — Action Required";
        String message = String.format(
            "Task \"%s\" has been stuck at %d%% for over %d hours. " +
            "Please review and update the progress or flag a blocker.",
            task.getTaskName(),
            task.getProgress(),
            event.getStuckDurationHours());

        // 1. Notify assigned student
        notifyUser(task.getAssignedTo(), title, message);

        // 2. Notify group leader (if not the same as assignee)
        var leader = group.getLeader();
        if (!leader.getId().equals(task.getAssignedTo().getId())) {
            notifyUser(leader, title, message);
        }

        // 3. Notify all active observers of the group
        List<GroupObserver> observers =
            groupObserverRepository.findActiveObserversByGroupId(group.getId());

        for (GroupObserver observer : observers) {
            notifyUser(observer.getUser(), title, message);
        }

        log.warn("SYSTEM WARNING: Frozen task {} (group {}) — {} recipients notified",
            task.getId(), group.getId(),
            1 + (leader.equals(task.getAssignedTo()) ? 0 : 1) + observers.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void notifyUser(User recipient, String title, String message) {
        notificationRepository.save(Notification.builder()
            .user(recipient)
            .title(title)
            .message(message)
            .type(NotificationType.SYSTEM_WARNING)
            .isRead(false)
            .build());
    }
}
