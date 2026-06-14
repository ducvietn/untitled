package com.teamup.teamup.service;

import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.GroupProgressDto;
import com.teamup.teamup.service.impl.GroupProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupProgressService.
 *
 * Covers: absolute %, weighted %, burndown series construction,
 * edge cases (empty group, same-start-deadline, over-deadline).
 */
@ExtendWith(MockitoExtension.class)
class GroupProgressServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupProgressService sut;

    private Group group;

    @BeforeEach
    void setUp() {
        group = Group.builder()
            .id(1L)
            .groupName("Team Alpha")
            .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Absolute and weighted completion %
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Completion percentage calculation")
    class CompletionPercentTests {

        @Test
        @DisplayName("All tasks DONE → absolute = 100%, weighted = 100%")
        void allDone_yields100() {
            List<Task> tasks = List.of(
                doneTask(1L, 2.0),
                doneTask(2L, 5.0),
                doneTask(3L, 3.0)
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getAbsoluteCompletionPercent()).isEqualTo(100.0);
            assertThat(result.getWeightedCompletionPercent()).isEqualTo(100.0);
            assertThat(result.getTotalTaskCount()).isEqualTo(3);
            assertThat(result.getDoneTaskCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("No tasks done → absolute = 0%, weighted = 0%")
        void noneDone_yieldsZero() {
            List<Task> tasks = List.of(
                makeTask(1L, 0, TaskStatus.TO_DO, 2.0, daysAgo(10), daysFromNow(10)),
                makeTask(2L, 25, TaskStatus.IN_PROGRESS, 8.0, daysAgo(8), daysFromNow(12))
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getAbsoluteCompletionPercent()).isZero();
            assertThat(result.getWeightedCompletionPercent()).isZero();
        }

        @Test
        @DisplayName("Mixed statuses: counts are accurate")
        void mixedStatuses_countsCorrect() {
            List<Task> tasks = List.of(
                makeTask(1L, 100, TaskStatus.DONE,         2.0, daysAgo(10), daysFromNow(10)),
                makeTask(2L,  50, TaskStatus.IN_PROGRESS,  3.0, daysAgo(8),  daysFromNow(12)),
                makeTask(3L, 100, TaskStatus.PENDING_REVIEW, 5.0, daysAgo(5), daysFromNow(7)),
                makeTask(4L,   0, TaskStatus.TO_DO,         1.0, daysAgo(2), daysFromNow(14))
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getTotalTaskCount()).isEqualTo(4);
            assertThat(result.getDoneTaskCount()).isEqualTo(2);       // DONE only (not PENDING_REVIEW)
            assertThat(result.getInProgressTaskCount()).isEqualTo(1);
            assertThat(result.getPendingReviewTaskCount()).isEqualTo(1);
            assertThat(result.getTodoTaskCount()).isEqualTo(1);
            assertThat(result.getAbsoluteCompletionPercent()).isEqualTo(50.0); // 2/4
        }

        @Test
        @DisplayName("Weighted % differs from absolute when task weights are unequal")
        void weightedDiffersFromAbsolute() {
            // Task A: 50% progress, 1h  → contribution = 50×1 = 50
            // Task B: 100% done,   9h  → contribution = 100×9 = 900
            // Weighted % = (50+900) / 10 = 95%
            // Absolute % = 1/2 = 50%
            List<Task> tasks = List.of(
                makeTask(1L, 50, TaskStatus.IN_PROGRESS, 1.0, daysAgo(5), daysFromNow(15)),
                doneTaskWithHours(2L, 9.0)
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getAbsoluteCompletionPercent()).isEqualTo(50.0);
            assertThat(result.getWeightedCompletionPercent()).isEqualTo(95.0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Burndown time-series
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Burndown series generation")
    class BurndownSeriesTests {

        @Test
        @DisplayName("Series starts at 0% and ends at 100% expected progress")
        void series_starts0_ends100() {
            List<Task> tasks = List.of(
                makeTask(1L, 0, TaskStatus.TO_DO, 2.0, daysAgo(5), daysFromNow(5))  // 10-day span
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            List<GroupProgressDto.BurndownPoint> series = result.getBurndownSeries();
            assertThat(series).isNotEmpty();
            assertThat(series.get(0).getExpectedProgress()).isEqualTo(0.0);
            // Last point: expected should be 100 (or max capped if over MAX_BURNDOWN_DAYS)
            GroupProgressDto.BurndownPoint last = series.get(series.size() - 1);
            assertThat(last.getExpectedProgress()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Future dates have null actualProgress")
        void futureDates_actualIsNull() {
            List<Task> tasks = List.of(
                makeTask(1L, 0, TaskStatus.TO_DO, 2.0, daysFromNow(1), daysFromNow(11))
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            boolean hasFutureWithActual = result.getBurndownSeries().stream()
                .filter(p -> !p.getDate().equals(java.time.LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)))
                .anyMatch(p -> p.getActualProgress() != null);

            assertThat(hasFutureWithActual).isFalse();
        }

        @Test
        @DisplayName("Today has non-null actualProgress")
        void today_hasActualProgress() {
            List<Task> tasks = List.of(
                makeTask(1L, 60, TaskStatus.IN_PROGRESS, 2.0, daysAgo(5), daysFromNow(5))
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            String today = java.time.LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            GroupProgressDto.BurndownPoint todayPoint = result.getBurndownSeries().stream()
                .filter(p -> p.getDate().equals(today))
                .findFirst()
                .orElseThrow();

            assertThat(todayPoint.getActualProgress()).isNotNull();
        }

        @Test
        @DisplayName("Empty group returns empty series")
        void emptyGroup_emptySeries() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(Collections.emptyList());

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getBurndownSeries()).isEmpty();
            assertThat(result.getTotalTaskCount()).isZero();
        }

        @Test
        @DisplayName("projectStartDate and finalDeadline are correctly extracted")
        void dateRange_extractedCorrectly() {
            LocalDateTime start = daysAgo(5);
            LocalDateTime end   = daysFromNow(20);
            List<Task> tasks = List.of(
                makeTask(1L, 0, TaskStatus.TO_DO, 1.0, start, end)
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getProjectStartDate())
                .isEqualTo(start.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            assertThat(result.getFinalDeadline())
                .isEqualTo(end.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        @Test
        @DisplayName("Deadline before start date — gracefully handled")
        void deadlineBeforeStart_gracefulFallback() {
            // deadline < createdAt — should not crash
            List<Task> tasks = List.of(
                makeTask(1L, 0, TaskStatus.TO_DO, 1.0, daysFromNow(5), daysAgo(5))
            );
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(tasks);

            GroupProgressDto result = sut.computeGroupProgress(1L);

            assertThat(result.getBurndownSeries()).isNotEmpty();
            assertThat(result.getProjectStartDate()).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private Task makeTask(Long id, int progress, TaskStatus status,
                          double hours, LocalDateTime createdAt, LocalDateTime deadline) {
        return Task.builder()
            .id(id)
            .taskName("Task-" + id)
            .progress(progress)
            .status(status)
            .estimatedHours(hours)
            .group(group)
            .assignedTo(null)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .deadline(deadline)
            .build();
    }

    private Task doneTask(Long id, double hours) {
        return Task.builder()
            .id(id)
            .taskName("Task-" + id)
            .progress(100)
            .status(TaskStatus.DONE)
            .estimatedHours(hours)
            .group(group)
            .assignedTo(null)
            .createdAt(daysAgo(5))
            .updatedAt(daysAgo(1))
            .deadline(daysFromNow(5))
            .build();
    }

    private Task doneTaskWithHours(Long id, double hours) {
        return Task.builder()
            .id(id)
            .taskName("Task-" + id)
            .progress(100)
            .status(TaskStatus.DONE)
            .estimatedHours(hours)
            .group(group)
            .assignedTo(null)
            .createdAt(daysAgo(5))
            .updatedAt(daysAgo(1))
            .deadline(daysFromNow(5))
            .build();
    }

    private LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }

    private LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days);
    }
}
