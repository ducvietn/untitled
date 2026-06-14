package com.teamup.teamup.service;

import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.BottleneckReportDto;
import com.teamup.teamup.service.dto.BottleneckReportDto.BottleneckTaskDto;
import com.teamup.teamup.service.impl.BottleneckDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BottleneckDetectionService.
 *
 * Covers: bottleneck classification, severity scoring,
 * dependency graph construction, edge cases.
 */
@ExtendWith(MockitoExtension.class)
class BottleneckDetectionServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private BottleneckDetectionService sut;

    private Group group;

    @BeforeEach
    void setUp() {
        group = Group.builder().id(1L).groupName("Team Beta").build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Bottleneck classification
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bottleneck classification")
    class BottleneckClassificationTests {

        @Test
        @DisplayName("Heavy task with low progress and dependents → IS a bottleneck")
        void heavyLowProgressWithDependents_isBottleneck() {
            // Task B depends on Task A; Task A is 20% done, 20 hours → severity = 20×0.8 = 16
            Task taskA = task("A", 20, 20.0, daysFromNow(5), TaskStatus.IN_PROGRESS, null);
            Task taskB = task("B", 0,  5.0,  daysFromNow(10), TaskStatus.TO_DO, taskA);
            // A is the dependency target, B is the dependent
            List<Task> allTasks     = List.of(taskA, taskB);
            List<Task> depTasks     = List.of(taskB); // only B has dependsOn

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(allTasks);
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(depTasks);

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getHasBottlenecks()).isTrue();
            assertThat(result.getBottlenecks()).hasSize(1);
            BottleneckTaskDto b = result.getBottlenecks().get(0);
            assertThat(b.getTaskId()).isEqualTo(taskA.getId());
            assertThat(b.getBlockedTaskIds()).containsExactly(taskB.getId());
            assertThat(b.getSeverityScore()).isEqualTo(16.0);
        }

        @Test
        @DisplayName("Urgent deadline task (< 3 days) with low progress → IS a bottleneck, even without dependents")
        void urgentLowProgress_noDependents_isBottleneck() {
            // Task C: 30% done, 3h, deadline in 1 day — urgent but light
            Task taskC = task("C", 30, 3.0, daysFromNow(1), TaskStatus.IN_PROGRESS, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(taskC));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getHasBottlenecks()).isTrue();
            assertThat(result.getBottlenecks()).hasSize(1);
            BottleneckTaskDto b = result.getBottlenecks().get(0);
            assertThat(b.getTaskId()).isEqualTo(taskC.getId());
            assertThat(b.getBlockedTaskCount()).isZero();
            assertThat(b.getOverdue()).isFalse();
        }

        @Test
        @DisplayName("Light task (weight < 4h) with no dependents and no urgency → NOT a bottleneck")
        void lightNoDependentsNoUrgency_notBottleneck() {
            Task light = task("D", 10, 2.0, daysFromNow(30), TaskStatus.IN_PROGRESS, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(light));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getHasBottlenecks()).isFalse();
            assertThat(result.getBottlenecks()).isEmpty();
        }

        @Test
        @DisplayName("High progress task (> 50%) with no urgency → NOT a bottleneck")
        void highProgress_notBottleneck() {
            Task advanced = task("E", 75, 20.0, daysFromNow(30), TaskStatus.IN_PROGRESS, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(advanced));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getHasBottlenecks()).isFalse();
        }

        @Test
        @DisplayName("Overdue task with low progress → overdue flag = true")
        void overdueTask_flaggedCorrectly() {
            Task overdue = task("F", 30, 10.0, daysAgo(2), TaskStatus.IN_PROGRESS, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(overdue));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getBottlenecks()).hasSize(1);
            assertThat(result.getBottlenecks().get(0).getOverdue()).isTrue();
        }

        @Test
        @DisplayName("Not started task (0%) with dependents → severity = estimatedHours")
        void zeroProgress_severityEqualsHours() {
            Task blocker = task("G", 0, 8.0, daysFromNow(5), TaskStatus.TO_DO, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(blocker));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getBottlenecks()).hasSize(1);
            assertThat(result.getBottlenecks().get(0).getSeverityScore()).isEqualTo(8.0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Severity ranking
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Severity ranking")
    class SeverityRankingTests {

        @Test
        @DisplayName("Multiple bottlenecks sorted by descending severity")
        void sortedByDescendingSeverity() {
            // Low severity: 30%, 6h  → (1-0.30)×6 = 4.2
            // High severity: 10%, 20h → (1-0.10)×20 = 18.0
            Task low  = task("L", 30, 6.0, daysFromNow(5), TaskStatus.IN_PROGRESS, null);
            Task high = task("H", 10, 20.0, daysFromNow(5), TaskStatus.IN_PROGRESS, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(low, high));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getBottlenecks()).hasSize(2);
            assertThat(result.getBottlenecks().get(0).getTaskName()).isEqualTo("Task-H");
            assertThat(result.getBottlenecks().get(1).getTaskName()).isEqualTo("Task-L");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Dependency graph builder
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dependency graph")
    class DependencyGraphTests {

        @Test
        @DisplayName("Task with multiple dependents: all dependents captured")
        void multipleDependents_allCaptured() {
            Task root = task("R", 20, 15.0, daysFromNow(5), TaskStatus.IN_PROGRESS, null);
            Task dep1 = task("D1", 0, 3.0, daysFromNow(10), TaskStatus.TO_DO, root);
            Task dep2 = task("D2", 0, 4.0, daysFromNow(10), TaskStatus.TO_DO, root);
            Task dep3 = task("D3", 0, 2.0, daysFromNow(10), TaskStatus.TO_DO, root);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L))
                .thenReturn(List.of(root, dep1, dep2, dep3));
            when(taskRepository.findTasksWithDependency(1L))
                .thenReturn(List.of(dep1, dep2, dep3));

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            BottleneckTaskDto bottleneck = result.getBottlenecks().get(0);
            assertThat(bottleneck.getBlockedTaskCount()).isEqualTo(3);
            assertThat(bottleneck.getBlockedTaskIds()).containsExactlyInAnyOrder(
                dep1.getId(), dep2.getId(), dep3.getId()
            );
        }

        @Test
        @DisplayName("Empty group: no bottlenecks")
        void emptyGroup_noBottlenecks() {
            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L))
                .thenReturn(Collections.emptyList());
            when(taskRepository.findTasksWithDependency(1L))
                .thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getHasBottlenecks()).isFalse();
            assertThat(result.getTotalBlockingTaskCount()).isZero();
            assertThat(result.getGroupName()).isEqualTo("Team Beta");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Reason string generation
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reason string")
    class ReasonStringTests {

        @Test
        @DisplayName("Reason includes 'Not started' for 0% progress")
        void zeroProgress_reasonContainsNotStarted() {
            Task t = task("Z", 0, 8.0, daysFromNow(5), TaskStatus.TO_DO, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(t));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getBottlenecks().get(0).getReason())
                .contains("Not started");
        }

        @Test
        @DisplayName("Reason includes blocked task count")
        void hasDependents_reasonContainsBlockedCount() {
            Task root = task("R", 20, 10.0, daysFromNow(5), TaskStatus.IN_PROGRESS, null);
            Task dep  = task("D", 0, 2.0, daysFromNow(10), TaskStatus.TO_DO, root);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(root, dep));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(List.of(dep));

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getBottlenecks().get(0).getReason())
                .contains("1 task(s) depend on this");
        }

        @Test
        @DisplayName("Reason includes deadline urgency")
        void urgentDeadline_reasonContainsDaysLeft() {
            Task t = task("U", 10, 3.0, daysFromNow(1), TaskStatus.IN_PROGRESS, null);

            when(taskRepository.findAllByGroupIdOrderByCreatedAt(1L)).thenReturn(List.of(t));
            when(taskRepository.findTasksWithDependency(1L)).thenReturn(Collections.emptyList());

            BottleneckReportDto result = sut.detectBottlenecks(1L);

            assertThat(result.getBottlenecks().get(0).getReason())
                .contains("Deadline in 1 day(s)");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private Task task(String name, int progress, double hours,
                      LocalDateTime deadline, TaskStatus status, Task dependsOn) {
        return Task.builder()
            .id((long) name.hashCode())
            .taskName("Task-" + name)
            .progress(progress)
            .estimatedHours(hours)
            .deadline(deadline)
            .status(status)
            .group(group)
            .assignedTo(User.builder().id(1L).name("Dev").build())
            .dependsOnTask(dependsOn)
            .createdAt(LocalDateTime.now().minusDays(3))
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days);
    }

    private LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }
}
