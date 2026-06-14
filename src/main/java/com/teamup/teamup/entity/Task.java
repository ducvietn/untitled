package com.teamup.teamup.entity;

import com.teamup.teamup.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Task — a unit of work assigned to a student within a Group.
 *
 * Optimistic locking via {@link BaseEntity#version} prevents concurrent
 * progress updates from silently overwriting each other.
 *
 * <pre>
 * FK index: idx_tasks_group_id       ON tasks(group_id)
 * FK index: idx_tasks_assigned_to    ON tasks(assigned_to)
 * FK index: idx_tasks_status         ON tasks(status)
 * FK index: idx_tasks_updated_at     ON tasks(updated_at)   — stale-task cron query
 * FK index: idx_tasks_depends_on     ON tasks(depends_on_task_id)
 * </pre>
 */
@Entity
@Table(
    name = "tasks",
    indexes = {
        @Index(name = "idx_tasks_group_id",    columnList = "group_id"),
        @Index(name = "idx_tasks_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_tasks_status",      columnList = "status"),
        @Index(name = "idx_tasks_updated_at",  columnList = "updated_at"),
        @Index(name = "idx_tasks_depends_on",  columnList = "depends_on_task_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity implements AuditableEntity {

    @Column(nullable = false, length = 150)
    private String taskName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime deadline;

    /**
     * 0–100 integer representing percentage completion.
     * Business rule: progress = 100 MUST be accompanied by a file upload
     * (enforced at Service layer).
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer progress = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.TO_DO;

    /**
     * Estimated hours (weight) for this task.
     * Used as the weight in the weighted-average contribution algorithm.
     * Must be &gt; 0. Defaults to 1.0 for tasks created before this field was introduced.
     */
    @Column(name = "estimated_hours", nullable = false)
    @Builder.Default
    private Double estimatedHours = 1.0;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "group_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tasks_group")
    )
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "assigned_to",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tasks_assigned_to")
    )
    private User assignedTo;

    /**
     * Self-referential dependency: this task cannot start meaningfully until
     * dependsOnTask is done. Used by BottleneckDetectionService to build a
     * dependency graph and detect blocked work.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "depends_on_task_id",
        foreignKey = @ForeignKey(name = "fk_tasks_depends_on")
    )
    private Task dependsOnTask;

    /**
     * Tasks that depend on THIS task (computed inverse side).
     */
    @OneToMany(mappedBy = "dependsOnTask", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Task> blockedTasks = new HashSet<>();

    /**
     * Submissions (file uploads) for this task.
     * Multiple submissions allowed (e.g. re-submit after rejection).
     */
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Submission> submissions = new HashSet<>();

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addSubmission(Submission submission) {
        submissions.add(submission);
        submission.setTask(this);
    }

    public boolean isDone() {
        return status == TaskStatus.DONE;
    }

    public boolean isAwaitingApproval() {
        return progress == 100 && status == TaskStatus.PENDING_REVIEW;
    }

    /**
     * Returns the full depth-first chain of tasks blocked by this task.
     */
    public List<Task> getBlockingChain() {
        List<Task> chain = new ArrayList<>();
        collectBlocking(chain, this);
        return chain;
    }

    private void collectBlocking(List<Task> acc, Task task) {
        for (Task blocked : task.getBlockedTasks()) {
            if (!acc.contains(blocked)) {
                acc.add(blocked);
                collectBlocking(acc, blocked);
            }
        }
    }
}
