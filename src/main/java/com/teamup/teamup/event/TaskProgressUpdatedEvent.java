package com.teamup.teamup.event;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a task's progress field is updated to 100%.
 *
 * The {@link com.teamup.teamup.event.NotificationEventListener} subscribes to this
 * event and dispatches notifications to the group leader and all observers.
 *
 * <h3>Why a Spring Application Event?</h3>
 * Decoupling: the {@code TaskService} does not need to know about notification logic.
 * It simply publishes the event. Any number of listeners can react without modifying
 * the task service.
 */
@Getter
public class TaskProgressUpdatedEvent extends ApplicationEvent {

    private final Task task;
    private final User updatedBy;
    private final int previousProgress;
    private final int newProgress;

    public TaskProgressUpdatedEvent(Object source, Task task,
                                    User updatedBy,
                                    int previousProgress,
                                    int newProgress) {
        super(source);
        this.task            = task;
        this.updatedBy       = updatedBy;
        this.previousProgress = previousProgress;
        this.newProgress     = newProgress;
    }
}
