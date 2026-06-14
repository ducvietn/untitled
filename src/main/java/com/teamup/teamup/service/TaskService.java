package com.teamup.teamup.service;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;

/**
 * Service interface for task lifecycle operations.
 */
public interface TaskService {

    /**
     * Updates the progress of a task.
     *
     * <h3>Business rules</h3>
     * <ul>
     *   <li>Only the task assignee or a teacher/admin can update progress.</li>
     *   <li>Progress is clamped to [0, 100].</li>
     *   <li>If progress transitions TO 100%, a {@code TaskProgressUpdatedEvent} is published
     *       so the group leader receives an in-app notification.</li>
     * </ul>
     *
     * @param taskId   the task to update
     * @param progress new progress value (0–100)
     * @param updater  the user making the change
     * @return the updated task
     */
    Task updateProgress(Long taskId, int progress, User updater);
}
