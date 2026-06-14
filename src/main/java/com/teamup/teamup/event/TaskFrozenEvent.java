package com.teamup.teamup.event;

import com.teamup.teamup.entity.Task;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when the 72-hour frozen-task cron job detects a task that has been
 * stuck at the same progress percentage for longer than 72 hours.
 *
 * The {@link com.teamup.teamup.event.NotificationEventListener} subscribes to this
 * event and creates a high-severity {@code SYSTEM_WARNING} notification for:
 * <ul>
 *   <li>the assigned student (who needs to act)</li>
 *   <li>the group leader (who needs to follow up)</li>
 *   <li>all observers of the task's group</li>
 * </ul>
 */
@Getter
public class TaskFrozenEvent extends ApplicationEvent {

    private final Task  task;
    private final long  stuckDurationHours;

    public TaskFrozenEvent(Object source, Task task, long stuckDurationHours) {
        super(source);
        this.task             = task;
        this.stuckDurationHours = stuckDurationHours;
    }
}
