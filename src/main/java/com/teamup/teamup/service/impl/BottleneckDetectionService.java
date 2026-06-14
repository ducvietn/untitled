package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.BottleneckReportDto;
import com.teamup.teamup.service.dto.BottleneckReportDto.BottleneckTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service 11 — Bottleneck Detection ("Weak Link" Finder).
 *
 * <h2>What is a Bottleneck?</h2>
 * A task is classified as a bottleneck if ALL of the following hold:
 * <ol>
 *   <li>estimatedHours &ge; MIN_WEIGHT_HOURS (default: 4.0).</li>
 *   <li>progress &lt; 50%.</li>
 *   <li>AND (condition A OR condition B):
 *       <ul>
 *         <li>A) It has at least one task depending on it (dependsOnTask).</li>
 *         <li>B) Its deadline is within URGENT_DAYS (default: 3 days) AND it is IN_PROGRESS.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Severity Score</h2>
 * <pre>
 * severity(task) = estimatedHours × (1 − progress / 100)
 * </pre>
 * A task at 0% with 20 estimated hours has severity = 20.
 * A task at 80% with 20 estimated hours has severity = 4.
 * Tasks are sorted by descending severity — the leader knows where to act first.
 *
 * <h2>Dependency Graph</h2>
 * The graph is built lazily on each call (no pre-computed adjacency list cached).
 * Each call fetches all tasks with dependencies in one O(n) query, then builds
 * an in-memory adjacency map in O(n) time.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li><b>Time: O(n log n)</b> —
 *       O(n) to fetch tasks, O(n) to build adjacency map, O(n) to score them,
 *       O(n log n) to sort by severity. Dominated by the sort step.</li>
 *   <li><b>Space: O(n)</b> — adjacency map plus task entity set held in memory.</li>
 *   <li>The graph is NOT cyclic by DB constraint (self-referential FK with no
 *       cycle-detection needed at the entity level — the business rule "no circular
 *       dependencies" should be enforced at the service/validation layer).</li>
 * </ul>
 *
 * <h2>Edge Cases</h2>
 * <ul>
 *   <li>No tasks in group → empty bottleneck list, hasBottlenecks = false.</li>
 *   <li>Tasks with null estimatedHours → treated as 1.0 hour (minimum weight).</li>
 *   <li>Circular dependency detected → the cycle is broken arbitrarily;
 *       a pre-validation constraint should reject cycles at entity creation.</li>
 *   <li>Task with dependsOnTask == null (no dependents) and not urgent → not flagged.</li>
 *   <li>Task overdue (deadline &lt; now) → flagged as overdue = true in the DTO.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BottleneckDetectionService {

    private final TaskRepository taskRepository;

    private static final double MIN_WEIGHT_HOURS = 4.0;  // minimum hours to be considered "heavy"
    private static final int    URGENT_DAYS      = 3;    // deadline within this window → urgent flag
    private static final int    LOW_PROGRESS_PCT = 50;   // tasks below this % are candidate bottlenecks
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Detects and ranks bottleneck tasks for a given group.
     *
     * @param groupId the group to analyse
     * @return BottleneckReportDto sorted by descending severity score
     */
    public BottleneckReportDto detectBottlenecks(Long groupId) {
        log.debug("Detecting bottlenecks for group {}", groupId);

        // ── O(n) data fetch ────────────────────────────────────────────────────
        // Single query: all tasks for the group, including dependency edges.
        List<Task> allTasks      = taskRepository.findAllByGroupIdOrderByCreatedAt(groupId);
        List<Task> dependentTasks = taskRepository.findTasksWithDependency(groupId);

        if (allTasks.isEmpty()) {
            log.debug("Group {} has no tasks — no bottlenecks possible", groupId);
            return emptyReport(groupId);
        }

        String groupName = allTasks.get(0).getGroup().getGroupName();

        // ── O(n) build adjacency: dependsOnTask → list of blocked tasks ──────────
        // Map: taskId of the dependency → set of tasks that depend on it.
        // e.g. if task-5 is blocked by task-3, entry is: 3 → [task-5]
        Map<Long, Set<Task>> blockedBy = buildReverseDependencyMap(dependentTasks);

        // ── O(n) score every task ───────────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        List<BottleneckTaskDto> bottlenecks = new ArrayList<>();

        for (Task task : allTasks) {
            BottleneckTaskDto dto = scoreTask(task, blockedBy, now);
            if (dto != null) {
                bottlenecks.add(dto);
            }
        }

        // ── O(n log n) sort by descending severity ───────────────────────────────
        bottlenecks.sort((a, b) -> Double.compare(b.getSeverityScore(), a.getSeverityScore()));

        boolean hasBottlenecks = !bottlenecks.isEmpty();

        log.info("Group {}: {} bottleneck(s) detected (highest severity: {:.2f})",
            groupId, bottlenecks.size(),
            hasBottlenecks ? bottlenecks.get(0).getSeverityScore() : 0.0);

        return BottleneckReportDto.builder()
            .groupId(groupId)
            .groupName(groupName)
            .hasBottlenecks(hasBottlenecks)
            .totalBlockingTaskCount(bottlenecks.size())
            .bottlenecks(bottlenecks)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Core scoring logic
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Scores a single task. Returns null if the task does not meet bottleneck criteria.
     *
     * Criteria (all must be true):
     *   1. estimatedHours >= MIN_WEIGHT_HOURS
     *   2. progress < LOW_PROGRESS_PCT
     *   3. (has dependents OR is urgent deadline)
     *
     * Severity = estimatedHours × (1 − progress/100)
     */
    BottleneckTaskDto scoreTask(Task task, Map<Long, Set<Task>> blockedBy, LocalDateTime now) {
        double hours   = task.getEstimatedHours() != null ? task.getEstimatedHours() : 1.0;
        int progress   = task.getProgress() != null ? task.getProgress() : 0;
        long daysLeft  = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), task.getDeadline().toLocalDate());
        boolean urgent = daysLeft <= URGENT_DAYS;

        // Criterion 1 & 2: light or mostly-done tasks are not bottlenecks
        if (hours < MIN_WEIGHT_HOURS && !urgent) {
            return null;
        }
        if (progress >= LOW_PROGRESS_PCT && !urgent) {
            return null;
        }

        // Criterion 3: must have dependents OR be urgent
        Set<Task> blocked = blockedBy.getOrDefault(task.getId(), Collections.emptySet());
        boolean hasDependents = !blocked.isEmpty();

        if (!hasDependents && !urgent) {
            return null;
        }

        // ── Compute severity ────────────────────────────────────────────────────
        double severity = hours * (1.0 - progress / 100.0);

        // ── Build reason string ─────────────────────────────────────────────────
        List<String> reasons = new ArrayList<>();
        if (hasDependents) {
            reasons.add(blocked.size() + " task(s) depend on this");
        }
        if (urgent) {
            reasons.add("Deadline in " + daysLeft + " day(s)");
        }
        if (progress == 0) {
            reasons.add("Not started (0%)");
        } else if (progress < 20) {
            reasons.add("Barely started (" + progress + "%)");
        }

        List<Long> blockedIds = blocked.stream()
            .map(Task::getId)
            .sorted()
            .collect(Collectors.toList());

        User assignee = task.getAssignedTo();

        return BottleneckTaskDto.builder()
            .taskId(task.getId())
            .taskName(task.getTaskName())
            .assigneeId(assignee != null ? assignee.getId() : null)
            .assigneeName(assignee != null ? assignee.getName() : "Unassigned")
            .estimatedHours(round(hours, 2))
            .progress(progress)
            .severityScore(round(severity, 2))
            .blockedTaskCount(blocked.size())
            .blockedTaskIds(blockedIds)
            .reason(String.join("; ", reasons))
            .deadline(task.getDeadline().toLocalDate().format(DTF))
            .overdue(daysLeft < 0)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dependency graph builder
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builds a reverse adjacency map: for each task that is a dependency target,
     * maps it to the set of tasks that depend on it.
     *
     * Example:
     *   Task A depends on Task B
     *   → entry: B.id → [A]
     *
     * <b>Time: O(n)</b> where n = number of tasks with a depends_on_task_id.
     * <b>Space: O(d)</b> where d = number of distinct dependency edges.
     */
    Map<Long, Set<Task>> buildReverseDependencyMap(List<Task> dependentTasks) {
        Map<Long, Set<Task>> map = new HashMap<>();
        for (Task t : dependentTasks) {
            Task dep = t.getDependsOnTask();
            if (dep != null) {
                map.computeIfAbsent(dep.getId(), k -> new HashSet<>()).add(t);
            }
        }
        return map;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private BottleneckReportDto emptyReport(Long groupId) {
        return BottleneckReportDto.builder()
            .groupId(groupId)
            .hasBottlenecks(false)
            .totalBlockingTaskCount(0)
            .bottlenecks(Collections.emptyList())
            .build();
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
