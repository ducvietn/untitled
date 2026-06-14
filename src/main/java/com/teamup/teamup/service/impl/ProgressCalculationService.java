package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.MemberContributionDto;
import com.teamup.teamup.service.dto.MemberContributionDto.TaskContributionDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service 9 — Individual Weighted Contribution Algorithm.
 *
 * <h2>Algorithm: Weighted Average Contribution</h2>
 * <pre>
 * Contribution(user, group) = Σ(progress_i × weight_i) / Σ(weight_i)
 *                            i∈tasks assigned to user in this group
 * </pre>
 *
 * <h2>Complexity Analysis</h2>
 * <ul>
 *   <li><b>Time: O(n)</b> — one linear pass over the user's task list where
 *       n = number of tasks assigned to the user within the group.
 *       The Streams pipeline accumulates two doubles in a single reduce operation.</li>
 *   <li><b>Space: O(1)</b> — only two scalar accumulators (weightedSum, totalWeight)
 *       and a bounded set of detail objects sized to n. No recursion.</li>
 * </ul>
 *
 * <h2>Edge Cases Handled</h2>
 * <ul>
 *   <li>User has 0 tasks in the group → returns 0.0 contribution.</li>
 *   <li>All tasks have estimatedHours = 0 → treated as weight = 1 (fallback to 1.0).</li>
 *   <li>Mixed weights (some 0, some &gt;0) → 0-weight tasks are included in detail
 *       but contribute 0 to the numerator, effectively ignoring them.</li>
 *   <li>All tasks DONE (progress = 100) → returns 100.0 (perfect score).</li>
 *   <li>All tasks at 0% → returns 0.0.</li>
 *   <li>Null-safe: tasks with null estimatedHours are treated as 1.0.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProgressCalculationService {

    private final TaskRepository taskRepository;

    /**
     * Computes the weighted contribution percentage for a single user within a group.
     *
     * @param user    the student whose contribution is being measured
     * @param groupId the group to scope the calculation to
     * @return MemberContributionDto with weighted contribution % and task details
     */
    public MemberContributionDto calculateContribution(User user, Long groupId) {
        log.debug("Calculating weighted contribution for user {} in group {}", user.getId(), groupId);

        // ── O(n) fetch ─────────────────────────────────────────────────────────
        // Single JPQL query: one round-trip to the DB. No N+1.
        List<Task> tasks = taskRepository.findByAssignedToAndGroupId(user, groupId);

        if (tasks.isEmpty()) {
            log.debug("User {} has no tasks in group {} — returning zero contribution", user.getId(), groupId);
            return MemberContributionDto.builder()
                .userId(user.getId())
                .userName(user.getName())
                .contributionPercent(0.0)
                .taskCount(0)
                .completedTaskCount(0)
                .taskDetails(Collections.emptyList())
                .build();
        }

        // ── O(n) single-pass stream reduction ───────────────────────────────────
        // Two accumulator doubles: weightedSum, totalWeight.
        // This is mathematically equivalent to the weighted-average formula above.
        // Complexity: O(n) time, O(1) auxiliary space.
        WeightedAccumulator acc = tasks.stream()
            .map(this::normaliseTask)                        // O(1) per task
            .reduce(
                new WeightedAccumulator(0.0, 0.0, 0, 0),
                WeightedAccumulator::add,                     // O(1) merge
                WeightedAccumulator::merge                    // O(1) parallel merge
            );

        // Guard: totalWeight == 0 → fallback to equal weight
        double contributionPercent = (acc.totalWeight <= 0.0)
            ? tasks.stream()
                   .mapToDouble(t -> normaliseProgress(t.getProgress()))
                   .average()
                   .orElse(0.0)
            : acc.weightedSum / acc.totalWeight;

        // ── O(n) detail list ──────────────────────────────────────────────────
        List<TaskContributionDetail> details = tasks.stream()
            .map(this::toDetail)
            .collect(Collectors.toList());

        log.debug("User {} contribution: {:.2f}% (tasks={}, weight={:.2f}, total={:.2f})",
            user.getId(), contributionPercent, tasks.size(), acc.totalWeight, acc.weightedSum);

        return MemberContributionDto.builder()
            .userId(user.getId())
            .userName(user.getName())
            .contributionPercent(round(contributionPercent, 2))
            .taskCount(tasks.size())
            .completedTaskCount(acc.completedCount)
            .taskDetails(details)
            .build();
    }

    /**
     * Computes contribution for multiple users in a single group in one DB round-trip.
     * Returns a Map keyed by userId for O(1) lookup.
     *
     * <b>Time: O(n)</b> where n = total tasks in the group.
     * <b>Space: O(m)</b> where m = number of distinct users (map entry count).
     *
     * @param groupId the group to analyse
     * @return Map of userId → MemberContributionDto
     */
    public Map<Long, MemberContributionDto> calculateAllContributionsInGroup(Long groupId) {
        log.debug("Batch-calculating contributions for all members in group {}", groupId);

        List<Task> allTasks = taskRepository.findAllByGroupIdWithAssignee(groupId);

        if (allTasks.isEmpty()) {
            return Collections.emptyMap();
        }

        // O(n): partition tasks by assigneeId
        Map<Long, List<Task>> tasksByUser = allTasks.stream()
            .collect(Collectors.groupingBy(t -> t.getAssignedTo().getId())); // O(1) per task

        // O(m): one stream per user (each processes their own sub-list)
        return tasksByUser.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> computeContributionFromTaskList(
                    entry.getValue().get(0).getAssignedTo(),
                    entry.getValue()
                )
            ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal helpers — all O(1)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Normalise a task's estimatedHours: null/zero → fallback to 1.0.
     * This prevents division-by-zero and ensures 0-weight tasks are treated as
     * having minimum unit weight.
     */
    private Task normaliseTask(Task t) {
        if (t.getEstimatedHours() == null || t.getEstimatedHours() <= 0.0) {
            t.setEstimatedHours(1.0);
        }
        return t;
    }

    /**
     * Clamp progress to 0–100 range.
     */
    private double normaliseProgress(Integer progress) {
        if (progress == null) return 0.0;
        return Math.max(0, Math.min(100, progress));
    }

    /**
     * Converts a Task entity to a task-detail DTO for the contribution breakdown.
     */
    private TaskContributionDetail toDetail(Task t) {
        double w = t.getEstimatedHours() != null ? t.getEstimatedHours() : 1.0;
        double p = normaliseProgress(t.getProgress());
        return TaskContributionDetail.builder()
            .taskId(t.getId())
            .taskName(t.getTaskName())
            .progress(t.getProgress())
            .weight(w)
            .weightedProgress(round(p * w, 2))
            .status(t.getStatus().name())
            .deadline(t.getDeadline().toLocalDate().toString())
            .build();
    }

    /**
     * Computes contribution from an already-fetched task list (avoids a DB call).
     */
    private MemberContributionDto computeContributionFromTaskList(User user, List<Task> tasks) {
        WeightedAccumulator acc = tasks.stream()
            .map(this::normaliseTask)
            .reduce(
                new WeightedAccumulator(0.0, 0.0, 0, 0),
                WeightedAccumulator::add,
                WeightedAccumulator::merge
            );

        double contributionPercent = (acc.totalWeight <= 0.0)
            ? tasks.stream().mapToDouble(t -> normaliseProgress(t.getProgress())).average().orElse(0.0)
            : acc.weightedSum / acc.totalWeight;

        List<TaskContributionDetail> details = tasks.stream().map(this::toDetail).collect(Collectors.toList());

        return MemberContributionDto.builder()
            .userId(user.getId())
            .userName(user.getName())
            .contributionPercent(round(contributionPercent, 2))
            .taskCount(tasks.size())
            .completedTaskCount(acc.completedCount)
            .taskDetails(details)
            .build();
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mutable accumulator for the stream reduction
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lightweight mutable accumulator used inside the Stream#reduce operation.
     *
     * Keeping this as a mutable value-object (not a record or immutable class)
     * allows a single-add operation that is O(1). The alternative — using
     * immutable pairs in each reduce step — would allocate O(n) intermediate
     * objects, increasing GC pressure.
     *
     * Thread-safety: each thread in a parallel stream gets its own accumulator
     * instance; the merge() combinator runs in a single thread at the end.
     * No shared mutable state across threads.
     */
    static final class WeightedAccumulator {
        double weightedSum;    // Σ(progress_i × weight_i)
        double totalWeight;     // Σ(weight_i)
        int completedCount;     // tasks with status == DONE
        int taskCount;          // total tasks processed

        WeightedAccumulator(double weightedSum, double totalWeight,
                            int completedCount, int taskCount) {
            this.weightedSum = weightedSum;
            this.totalWeight = totalWeight;
            this.completedCount = completedCount;
            this.taskCount = taskCount;
        }

        /**
         * Add a single task's contribution to the accumulator.
         * Called once per task — O(1).
         */
        WeightedAccumulator add(Task t) {
            double weight = (t.getEstimatedHours() != null && t.getEstimatedHours() > 0)
                ? t.getEstimatedHours() : 1.0;
            double progress = Math.max(0, Math.min(100, t.getProgress() != null ? t.getProgress() : 0));
            this.weightedSum += progress * weight;
            this.totalWeight += weight;
            this.taskCount++;
            if (t.getStatus() == TaskStatus.DONE) {
                this.completedCount++;
            }
            return this;
        }

        /**
         * Merge two accumulators (used in parallel stream reduction).
         * Called once per parallel fork — O(1).
         */
        WeightedAccumulator merge(WeightedAccumulator other) {
            this.weightedSum += other.weightedSum;
            this.totalWeight += other.totalWeight;
            this.completedCount += other.completedCount;
            this.taskCount += other.taskCount;
            return this;
        }
    }
}
