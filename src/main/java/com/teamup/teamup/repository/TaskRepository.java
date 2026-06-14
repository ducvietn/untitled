package com.teamup.teamup.repository;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Task entities.
 * Custom queries are optimised for the three metric services:
 *   - ProgressCalculationService  (weighted average per user)
 *   - GroupProgressService       (burndown time-series)
 *   - BottleneckDetectionService (dependency graph traversal)
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ══════════════════════════════════════════════════════════════════════════
    // ProgressCalculationService queries
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * All tasks assigned to a specific user within a specific group.
     * Used for weighted-average contribution calculation.
     *
     * SELECT t FROM Task t JOIN FETCH t.group g WHERE t.assignedTo = :user AND g.id = :groupId
     */
    @Query("""
        SELECT t FROM Task t
        JOIN FETCH t.group g
        WHERE t.assignedTo = :user AND g.id = :groupId
        """)
    List<Task> findByAssignedToAndGroupId(
        @Param("user") User user,
        @Param("groupId") Long groupId
    );

    /**
     * All tasks for a group (all members) — used as a fast batch source for
     * the weighted group-progress aggregation.
     */
    @Query("""
        SELECT t FROM Task t
        JOIN FETCH t.assignedTo a
        WHERE t.group.id = :groupId
        ORDER BY t.createdAt ASC
        """)
    List<Task> findAllByGroupIdWithAssignee(@Param("groupId") Long groupId);

    // ══════════════════════════════════════════════════════════════════════════
    // GroupProgressService queries
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * All tasks for a group ordered by creation date — used to build the
     * burndown time-series. JOIN FETCH avoids N+1 on group access.
     */
    @Query("""
        SELECT t FROM Task t
        WHERE t.group.id = :groupId
        ORDER BY t.createdAt ASC
        """)
    List<Task> findAllByGroupIdOrderByCreatedAt(@Param("groupId") Long groupId);

    /**
     * Tasks with deadline inside a window — used by the God Eye dashboard
     * to flag at-risk groups.
     */
    @Query("""
        SELECT t FROM Task t
        JOIN FETCH t.group g
        JOIN FETCH g.classEntity c
        WHERE c.id = :classId
          AND t.deadline BETWEEN :now AND :deadlineCutoff
        ORDER BY t.deadline ASC
        """)
    List<Task> findTasksNearingDeadline(
        @Param("classId") Long classId,
        @Param("now") LocalDateTime now,
        @Param("deadlineCutoff") LocalDateTime deadlineCutoff
    );

    // ══════════════════════════════════════════════════════════════════════════
    // BottleneckDetectionService queries
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * All tasks for a group that have a dependency (depends_on_task_id IS NOT NULL).
     * Used to build the incoming-edge view of the dependency graph.
     */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.dependsOnTask dep
        WHERE t.group.id = :groupId AND t.dependsOnTask IS NOT NULL
        """)
    List<Task> findTasksWithDependency(@Param("groupId") Long groupId);

    /**
     * Tasks whose progress is below a threshold — candidate bottlenecks.
     * Default threshold used by BottleneckDetectionService: 50%.
     */
    @Query("""
        SELECT t FROM Task t
        JOIN FETCH t.assignedTo a
        WHERE t.group.id = :groupId
          AND t.progress < :threshold
          AND t.status <> com.teamup.teamup.enums.TaskStatus.DONE
        """)
    List<Task> findLowProgressTasks(
        @Param("groupId") Long groupId,
        @Param("threshold") Integer threshold
    );

    /**
     * Count DONE tasks for a group — used to compute absolute group completion %.
     */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.group.id = :groupId AND t.status = :status
        """)
    long countByGroupIdAndStatus(
        @Param("groupId") Long groupId,
        @Param("status") TaskStatus status
    );

    /**
     * Tasks that were last updated before a cutoff — stale tasks for the
     * cron "Referee" job (Feature 4).
     */
    @Query("""
        SELECT t FROM Task t
        WHERE t.status = :status
          AND t.progress < 100
          AND t.updatedAt < :cutoff
        """)
    List<Task> findStaleTasks(
        @Param("status") TaskStatus status,
        @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Projection: fetches ONLY the group's primary key for a task.
     * Used by SubjectAuthorizationService for O(1) task → group scope lookups.
     */
    @Query("SELECT t.group.id FROM Task t WHERE t.id = :taskId")
    java.util.Optional<Long> findGroupIdById(@Param("taskId") Long taskId);
}
