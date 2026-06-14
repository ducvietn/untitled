package com.teamup.teamup.scheduler;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.event.TaskFrozenEvent;
import com.teamup.teamup.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cron-based "Referee" job that monitors task health.
 *
 * <h3>Feature 15 — Frozen Task Detection</h3>
 * Every hour, this job queries for tasks that have been sitting at the same
 * non-100% progress level for longer than 72 hours. When found, it publishes
 * a {@link TaskFrozenEvent} which the {@link com.teamup.teamup.event.NotificationEventListener}
 * handles asynchronously — sending {@code SYSTEM_WARNING} notifications to:
 * <ul>
 *   <li>the assigned student (primary actor)</li>
 *   <li>the group leader (for oversight)</li>
 *   <li>all active observers of the group</li>
 * </ul>
 *
 * <h3>Scheduling</h3>
 * Runs at the top of every hour using Spring's {@code @Scheduled}.
 * In production, replace this with a proper distributed scheduler ( ShedLock,
 * Quartz) to prevent duplicate runs on multi-instance deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FrozenTaskScheduler {

    private final TaskRepository        taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Freeze threshold in hours. Tasks unchanged for longer than this → frozen alert. */
    private static final long FROZEN_THRESHOLD_HOURS = 72;

    /**
     * Runs every hour. Checks for IN_PROGRESS tasks that have not been
     * updated in the last 72 hours and are below 100% progress.
     *
     * Cron: "0 0 * * * *" = top of every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void detectFrozenTasks() {
        log.info("Frozen-task cron starting...");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(FROZEN_THRESHOLD_HOURS);

        // Find all non-done tasks that haven't been updated since the cutoff
        List<Task> frozenTasks = taskRepository.findStaleTasks(
            TaskStatus.IN_PROGRESS, cutoff);

        if (frozenTasks.isEmpty()) {
            log.debug("No frozen tasks detected.");
            return;
        }

        log.warn("{} frozen task(s) detected — publishing TaskFrozenEvent(s)",
            frozenTasks.size());

        for (Task task : frozenTasks) {
            eventPublisher.publishEvent(new TaskFrozenEvent(
                this, task, FROZEN_THRESHOLD_HOURS));
        }

        log.info("Frozen-task cron finished. {} event(s) published.", frozenTasks.size());
    }
}
