package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.Class;
import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.BottleneckReportDto;
import com.teamup.teamup.service.dto.GodEyeDashboardDto;
import com.teamup.teamup.service.dto.GodEyeDashboardDto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service 7 (aggregated) — God Eye Dashboard.
 *
 * Aggregates data from ProgressCalculationService, GroupProgressService,
 * and BottleneckDetectionService to produce a class-wide health overview
 * for the teacher.
 *
 * <h2>At-Risk Conditions (trigger RED health status)</h2>
 * <ul>
 *   <li>Average group progress &lt; 30% AND final deadline ≤ 2 days away.</li>
 *   <li>3 or more stale tasks (not updated in 72 h) in a single group.</li>
 *   <li>2 or more bottleneck tasks detected.</li>
 * </ul>
 *
 * <h2>Yellow Conditions (trigger YELLOW health status)</h2>
 * <ul>
 *   <li>Any task in PENDING_REVIEW state.</li>
 *   <li>Average group progress &lt; 50% with deadline ≤ 5 days away.</li>
 *   <li>1 bottleneck task detected.</li>
 * </ul>
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li><b>Time: O(G × n)</b> — G groups, each fetching and processing O(n) tasks.
 *     Bottleneck detection is O(n log n) per group. Capped by class size.</li>
 *   <li><b>Space: O(G)</b> — one output DTO per group.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GodEyeDashboardService {

    private final GroupRepository    groupRepository;
    private final TaskRepository    taskRepository;
    private final ProgressCalculationService  progressService;
    private final GroupProgressService       groupProgressService;
    private final BottleneckDetectionService  bottleneckService;

    private static final int STALE_THRESHOLD_HOURS = 72;
    private static final int RED_DEADLINE_DAYS     = 2;
    private static final int YELLOW_DEADLINE_DAYS  = 5;
    private static final int RED_PROGRESS_PCT      = 30;
    private static final int YELLOW_PROGRESS_PCT   = 50;

    /**
     * Produces the full class-level God Eye Dashboard.
     *
     * @param classEntity the class to analyse
     * @return GodEyeDashboardDto with per-group health cards
     */
    public GodEyeDashboardDto buildDashboard(Class classEntity) {
        log.debug("Building God Eye dashboard for class {}", classEntity.getId());

        List<Group> groups = groupRepository.findAllByClassIdWithMembers(classEntity.getId());

        List<GroupHealthDto> healthCards = groups.stream()
            .map(this::analyseGroup)
            .collect(Collectors.toList());

        List<GroupHealthDto> atRisk = healthCards.stream()
            .filter(g -> g.getHealthStatus() != HealthStatus.GREEN)
            .collect(Collectors.toList());

        Summary summary = computeSummary(healthCards);

        log.info("God Eye dashboard for class {}: {} groups, {} at-risk",
            classEntity.getId(), groups.size(), atRisk.size());

        return GodEyeDashboardDto.builder()
            .classId(classEntity.getId())
            .className(classEntity.getClassName())
            .semester(classEntity.getSemester())
            .groups(healthCards)
            .atRiskGroups(atRisk)
            .summary(summary)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Per-group analysis
    // ═══════════════════════════════════════════════════════════════════════════

    private GroupHealthDto analyseGroup(Group group) {
        Long groupId = group.getId();

        // O(n) tasks fetch
        List<Task> tasks = taskRepository.findAllByGroupIdOrderByCreatedAt(groupId);
        LocalDateTime staleCutoff = LocalDateTime.now().minusHours(STALE_THRESHOLD_HOURS);
        LocalDateTime now = LocalDateTime.now();

        int totalTasks = tasks.size();
        int doneTasks  = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        int staleTasks = (int) tasks.stream()
            .filter(t -> t.getStatus() != TaskStatus.DONE)
            .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().isBefore(staleCutoff))
            .count();
        int overdueTasks = (int) tasks.stream()
            .filter(t -> t.getStatus() != TaskStatus.DONE)
            .filter(t -> t.getDeadline().isBefore(now))
            .count();
        int pendingReviews = (int) tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING_REVIEW)
            .count();

        // O(n) weighted progress
        double weightedPct = computeWeightedProgress(tasks);
        double avgProgress = totalTasks > 0
            ? tasks.stream().mapToInt(t -> t.getProgress() != null ? t.getProgress() : 0).average().orElse(0.0)
            : 0.0;

        // O(n log n) bottleneck
        BottleneckReportDto bottleneck = bottleneckService.detectBottlenecks(groupId);

        // ── Determine health status ──────────────────────────────────────────────
        List<String> riskFlags = new ArrayList<>();
        HealthStatus status = determineHealth(
            avgProgress, tasks, staleTasks,
            pendingReviews, bottleneck.getBottlenecks().size(), riskFlags
        );

        return GroupHealthDto.builder()
            .groupId(groupId)
            .groupName(group.getGroupName())
            .leaderId(group.getLeader().getId())
            .leaderName(group.getLeader().getName())
            .healthStatus(status)
            .weightedProgressPercent(round(weightedPct, 2))
            .taskCount(totalTasks)
            .doneTaskCount(doneTasks)
            .staleTaskCount(staleTasks)
            .overdueTaskCount(overdueTasks)
            .pendingReviewCount(pendingReviews)
            .bottleneckReport(bottleneck)
            .riskFlags(riskFlags)
            .build();
    }

    private HealthStatus determineHealth(
        double avgProgress,
        List<Task> tasks,
        int staleTaskCount,
        int pendingReviews,
        int bottleneckCount,
        List<String> riskFlags
    ) {
        // Find the nearest deadline among unfinished tasks
        LocalDateTime nearestDeadline = tasks.stream()
            .filter(t -> t.getStatus() != TaskStatus.DONE)
            .map(Task::getDeadline)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now().plusDays(30));

        long daysToDeadline = java.time.temporal.ChronoUnit.DAYS.between(
            LocalDateTime.now(), nearestDeadline);

        // RED conditions
        if (avgProgress < RED_PROGRESS_PCT && daysToDeadline <= RED_DEADLINE_DAYS) {
            riskFlags.add("Low progress (" + round(avgProgress, 0) + "%) with deadline ≤ " + RED_DEADLINE_DAYS + " days");
            return HealthStatus.RED;
        }
        if (staleTaskCount >= 3) {
            riskFlags.add(staleTaskCount + " stale tasks (no update in " + STALE_THRESHOLD_HOURS + "h)");
            return HealthStatus.RED;
        }
        if (bottleneckCount >= 2) {
            riskFlags.add(bottleneckCount + " bottleneck tasks detected");
            return HealthStatus.RED;
        }

        // YELLOW conditions
        if (pendingReviews > 0) {
            riskFlags.add(pendingReviews + " task(s) awaiting leader review");
        }
        if (avgProgress < YELLOW_PROGRESS_PCT && daysToDeadline <= YELLOW_DEADLINE_DAYS) {
            riskFlags.add("Lagging behind schedule (" + round(avgProgress, 0) + "%)");
        }
        if (staleTaskCount > 0) {
            riskFlags.add(staleTaskCount + " stale task(s)");
        }
        if (bottleneckCount == 1) {
            riskFlags.add("1 bottleneck task detected — leader should follow up");
        }

        return (!riskFlags.isEmpty()) ? HealthStatus.YELLOW : HealthStatus.GREEN;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Summary aggregation
    // ═══════════════════════════════════════════════════════════════════════════

    private Summary computeSummary(List<GroupHealthDto> groups) {
        int n = groups.size();
        double avgProg = n > 0
            ? groups.stream().mapToDouble(g -> g.getWeightedProgressPercent() != null
                ? g.getWeightedProgressPercent() : 0.0).average().orElse(0.0)
            : 0.0;

        return Summary.builder()
            .totalGroups(n)
            .healthyGroups((int) groups.stream().filter(g -> g.getHealthStatus() == HealthStatus.GREEN).count())
            .atRiskGroups((int) groups.stream().filter(g -> g.getHealthStatus() != HealthStatus.GREEN).count())
            .totalTasks(groups.stream().mapToInt(GroupHealthDto::getTaskCount).sum())
            .overdueTasks(groups.stream().mapToInt(g -> g.getOverdueTaskCount() != null ? g.getOverdueTaskCount() : 0).sum())
            .staleTaskCount(groups.stream().mapToInt(g -> g.getStaleTaskCount() != null ? g.getStaleTaskCount() : 0).sum())
            .pendingReviewTasks(groups.stream().mapToInt(g -> g.getPendingReviewCount() != null ? g.getPendingReviewCount() : 0).sum())
            .averageGroupProgress(round(avgProg, 2))
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private double computeWeightedProgress(List<Task> tasks) {
        double weightedSum = 0.0, totalWeight = 0.0;
        for (Task t : tasks) {
            double w = t.getEstimatedHours() != null && t.getEstimatedHours() > 0
                ? t.getEstimatedHours() : 1.0;
            double p = Math.max(0, Math.min(100, t.getProgress() != null ? t.getProgress() : 0));
            weightedSum += p * w;
            totalWeight += w;
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
