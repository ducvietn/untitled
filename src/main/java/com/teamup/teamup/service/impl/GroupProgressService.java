package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.GroupProgressDto;
import com.teamup.teamup.service.dto.GroupProgressDto.BurndownPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service 10 — Group Progress & Burndown Chart Data.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Absolute group completion % = # DONE tasks / total tasks.</li>
 *   <li>Weighted group completion % (same formula as ProgressCalculationService
 *       but aggregated across all members).</li>
 *   <li>Burndown time-series: daily data points from project start to final deadline.</li>
 * </ol>
 *
 * <h2>Burndown Algorithm</h2>
 * For each calendar day D in [projectStartDate, today]:
 * <pre>
 * expectedProgress(D) = clamp(100 × (D − start) / (deadline − start), 0, 100)
 * actualProgress(D)   = # DONE tasks as of D / total tasks × 100
 * </pre>
 * The "as-of" actual progress requires snapshot data. Since we store only
 * current progress (not historical), we use a conservative approximation:
 * actualProgress(D) = max(0, currentDoneCount − overdueCountAfter(D)).
 * In production this should be replaced by a separate TaskProgressHistory
 * snapshot table; the current implementation marks future points as null.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li><b>Time: O(n log n)</b> — one DB query (O(n) rows) plus sorting by date.
 *     The burndown series construction is O(d) where d = days between start and
 *     deadline (bounded to ≤ 180 days to prevent runaway loops).</li>
 *   <li><b>Space: O(d)</b> — burndown series array of d daily points.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GroupProgressService {

    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;

    private static final int MAX_BURNDOWN_DAYS = 180; // safety cap on series length
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Computes full group progress including the burndown time-series.
     *
     * @param groupId the group to analyse
     * @return GroupProgressDto with absolute %, weighted %, and burndown data
     * @throws NoSuchElementException if the group does not exist
     */
    public GroupProgressDto computeGroupProgress(Long groupId) {
        log.debug("Computing group progress for group {}", groupId);

        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));

        // ── O(n) data fetch ────────────────────────────────────────────────────
        List<Task> tasks = taskRepository.findAllByGroupIdOrderByCreatedAt(groupId);

        if (tasks.isEmpty()) {
            return emptyProgressDto(group);
        }

        // ── O(n) aggregates ───────────────────────────────────────────────────
        long totalCount = tasks.size();
        long doneCount  = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long inProgCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long reviewCount  = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING_REVIEW).count();
        long todoCount    = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TO_DO).count();

        // ── Weighted completion % — O(n) ──────────────────────────────────────
        double weightedSum   = 0.0;
        double totalWeight  = 0.0;
        for (Task t : tasks) {
            double w = (t.getEstimatedHours() != null && t.getEstimatedHours() > 0)
                ? t.getEstimatedHours() : 1.0;
            double p = Math.max(0, Math.min(100, t.getProgress() != null ? t.getProgress() : 0));
            weightedSum  += p * w;
            totalWeight += w;
        }
        double weightedPct = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
        double absolutePct = totalCount > 0 ? (doneCount * 100.0 / totalCount) : 0.0;

        // ── O(n) date range ───────────────────────────────────────────────────
        LocalDate projectStart = tasks.stream()
            .map(t -> t.getCreatedAt().toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now());

        LocalDate finalDeadline = tasks.stream()
            .map(t -> t.getDeadline().toLocalDate())
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now().plusDays(30));

        // ── O(d) burndown series ───────────────────────────────────────────────
        // d is capped at MAX_BURNDOWN_DAYS to prevent memory overflow.
        long totalDays = ChronoUnit.DAYS.between(projectStart, finalDeadline);
        if (totalDays < 0) totalDays = 1; // deadline before start — edge case

        int cappedDays = (int) Math.min(totalDays, MAX_BURNDOWN_DAYS);
        double stepPercent = (totalDays > 0 && totalDays <= MAX_BURNDOWN_DAYS)
            ? 100.0 / totalDays
            : 100.0 / cappedDays;

        LocalDate today = LocalDate.now();
        List<BurndownPoint> series = new ArrayList<>(cappedDays + 1);

        for (int i = 0; i <= cappedDays; i++) {
            LocalDate pointDate = projectStart.plusDays(i);

            // Expected (linear ideal line)
            double expected = Math.min(100.0, Math.max(0.0, stepPercent * i));

            // Actual: only available for today or past
            Double actual = null;
            if (!pointDate.isAfter(today)) {
                // Approximation: use cumulative done tasks as of today.
                // In a full implementation, a TaskProgressSnapshot table would
                // supply per-day historical progress.
                if (!pointDate.isBefore(today)) {
                    actual = round(absolutePct, 2);
                } else {
                    // Past: use linear interpolation from start to current
                    double progressUptodate = round(
                        Math.min(100.0, stepPercent * ChronoUnit.DAYS.between(projectStart, today)), 2);
                    actual = round(Math.min(progressUptodate, absolutePct), 2);
                }
            }

            series.add(BurndownPoint.builder()
                .date(pointDate.format(DATE_FMT))
                .expectedProgress(round(expected, 2))
                .actualProgress(actual)
                .build());
        }

        log.debug("Group {} progress: absolute={:.2f}%, weighted={:.2f}%, burndown points={}",
            groupId, absolutePct, weightedPct, series.size());

        return GroupProgressDto.builder()
            .groupId(groupId)
            .groupName(group.getGroupName())
            .absoluteCompletionPercent(round(absolutePct, 2))
            .weightedCompletionPercent(round(weightedPct, 2))
            .totalTaskCount((int) totalCount)
            .doneTaskCount((int) doneCount)
            .inProgressTaskCount((int) inProgCount)
            .pendingReviewTaskCount((int) reviewCount)
            .todoTaskCount((int) todoCount)
            .projectStartDate(projectStart.format(DATE_FMT))
            .finalDeadline(finalDeadline.format(DATE_FMT))
            .burndownSeries(series)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private GroupProgressDto emptyProgressDto(Group group) {
        return GroupProgressDto.builder()
            .groupId(group.getId())
            .groupName(group.getGroupName())
            .absoluteCompletionPercent(0.0)
            .weightedCompletionPercent(0.0)
            .totalTaskCount(0)
            .doneTaskCount(0)
            .inProgressTaskCount(0)
            .pendingReviewTaskCount(0)
            .todoTaskCount(0)
            .burndownSeries(Collections.emptyList())
            .build();
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
