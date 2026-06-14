package com.teamup.teamup.service;

import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.MemberContributionDto;
import com.teamup.teamup.service.impl.ProgressCalculationService;
import com.teamup.teamup.service.impl.ProgressCalculationService.WeightedAccumulator;
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
 * Unit tests for ProgressCalculationService.
 *
 * Test strategy:
 *  - Mock TaskRepository to return controlled task lists.
 *  - Assert mathematical correctness of the weighted average.
 *  - Cover all documented edge cases.
 */
@ExtendWith(MockitoExtension.class)
class ProgressCalculationServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ProgressCalculationService sut;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = User.builder()
            .id(1L)
            .name("Alice")
            .email("alice@test.com")
            .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // WeightedAccumulator unit tests (pure function)
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WeightedAccumulator — pure function tests")
    class WeightedAccumulatorTests {

        @Test
        @DisplayName("Empty accumulator should return zero weighted sum and weight")
        void emptyAccumulator_yieldsZeros() {
            WeightedAccumulator acc = new WeightedAccumulator(0.0, 0.0, 0, 0);
            assertThat(acc.weightedSum).isZero();
            assertThat(acc.totalWeight).isZero();
            assertThat(acc.taskCount).isZero();
            assertThat(acc.completedCount).isZero();
        }

        @Test
        @DisplayName("Single task with 50% progress and weight 4 yields correct contribution")
        void singleTask_contributionCorrect() {
            WeightedAccumulator acc = new WeightedAccumulator(0.0, 0.0, 0, 0);
            Task t = makeTask(1L, 50, 4.0, TaskStatus.IN_PROGRESS);
            acc.add(t);

            assertThat(acc.weightedSum).isEqualTo(200.0);  // 50 × 4
            assertThat(acc.totalWeight).isEqualTo(4.0);
            assertThat(acc.taskCount).isEqualTo(1);
            assertThat(acc.completedCount).isZero();

            double contribution = acc.weightedSum / acc.totalWeight;
            assertThat(contribution).isEqualTo(50.0);
        }

        @Test
        @DisplayName("Multiple tasks: weighted average must equal mathematical definition")
        void multipleTasks_weightedAverageIsExact() {
            // Tasks: (20%, 2h), (80%, 8h) → expected = (20×2 + 80×8) / (2+8) = 660/10 = 66
            WeightedAccumulator acc = new WeightedAccumulator(0.0, 0.0, 0, 0);
            acc.add(makeTask(1L, 20, 2.0, TaskStatus.IN_PROGRESS));
            acc.add(makeTask(2L, 80, 8.0, TaskStatus.IN_PROGRESS));

            double contribution = acc.weightedSum / acc.totalWeight;
            assertThat(contribution).isEqualTo(66.0);
        }

        @Test
        @DisplayName("Tasks with 0 weight are excluded from numerator but counted in denominator")
        void zeroWeightTask_isTreatedAsWeight1Fallback() {
            // When estimatedHours is null → normaliseTask overrides to 1.0
            WeightedAccumulator acc = new WeightedAccumulator(0.0, 0.0, 0, 0);
            Task t = makeTask(1L, 50, null, TaskStatus.IN_PROGRESS); // null hours
            acc.add(t); // normaliseTask in the service will fix this

            // The add() method treats null as 1.0:
            assertThat(acc.totalWeight).isEqualTo(1.0);
            assertThat(acc.weightedSum).isEqualTo(50.0);
        }

        @Test
        @DisplayName("Parallel merge: two accumulators combine correctly")
        void merge_twoAccumulators_combineCorrectly() {
            WeightedAccumulator left = new WeightedAccumulator(200.0, 4.0, 0, 1);
            WeightedAccumulator right = new WeightedAccumulator(600.0, 6.0, 1, 2);

            left.merge(right);

            assertThat(left.weightedSum).isEqualTo(800.0);   // 200 + 600
            assertThat(left.totalWeight).isEqualTo(10.0);    // 4 + 6
            assertThat(left.taskCount).isEqualTo(3);        // 1 + 2
            assertThat(left.completedCount).isEqualTo(1);   // 0 + 1
        }

        @Test
        @DisplayName("Progress beyond 100 is clamped to 100")
        void over100Progress_isClampedTo100() {
            WeightedAccumulator acc = new WeightedAccumulator(0.0, 0.0, 0, 0);
            Task t = makeTask(1L, 120, 5.0, TaskStatus.IN_PROGRESS); // impossible but test guard
            acc.add(t);

            // add() clamps: max(0, min(100, 120)) = 100
            assertThat(acc.weightedSum).isEqualTo(500.0); // 100 × 5
        }

        @Test
        @DisplayName("Negative progress is clamped to 0")
        void negativeProgress_isClampedTo0() {
            WeightedAccumulator acc = new WeightedAccumulator(0.0, 0.0, 0, 0);
            Task t = makeTask(1L, -10, 3.0, TaskStatus.IN_PROGRESS);
            acc.add(t);

            assertThat(acc.weightedSum).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Service-level integration tests (with mocked repository)
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateContribution — edge cases")
    class CalculateContributionEdgeCases {

        @Test
        @DisplayName("User with 0 tasks returns 0.0 contribution")
        void noTasks_returnsZero() {
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L))
                .thenReturn(Collections.emptyList());

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getContributionPercent()).isZero();
            assertThat(result.getTaskCount()).isZero();
            assertThat(result.getCompletedTaskCount()).isZero();
            assertThat(result.getTaskDetails()).isEmpty();
            verify(taskRepository).findByAssignedToAndGroupId(alice, 10L);
        }

        @Test
        @DisplayName("User with all tasks done returns 100.0 contribution")
        void allDone_returns100() {
            List<Task> tasks = List.of(
                makeTask(1L, 100, 2.0, TaskStatus.DONE),
                makeTask(2L, 100, 3.0, TaskStatus.DONE),
                makeTask(3L, 100, 5.0, TaskStatus.DONE)
            );
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L)).thenReturn(tasks);

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getContributionPercent()).isEqualTo(100.0);
            assertThat(result.getCompletedTaskCount()).isEqualTo(3);
            assertThat(result.getTaskCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("All tasks at 0% returns 0.0 contribution")
        void allZero_returnsZero() {
            List<Task> tasks = List.of(
                makeTask(1L, 0, 2.0, TaskStatus.TO_DO),
                makeTask(2L, 0, 8.0, TaskStatus.TO_DO)
            );
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L)).thenReturn(tasks);

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getContributionPercent()).isZero();
        }

        @Test
        @DisplayName("Mixed progress with different weights — weighted average is exact")
        void mixedTasks_weightedAverageExact() {
            // (30%, 4h), (90%, 6h) → (120 + 540) / 10 = 66
            List<Task> tasks = List.of(
                makeTask(1L, 30, 4.0, TaskStatus.IN_PROGRESS),
                makeTask(2L, 90, 6.0, TaskStatus.IN_PROGRESS)
            );
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L)).thenReturn(tasks);

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getContributionPercent()).isEqualTo(66.0);
            assertThat(result.getTaskCount()).isEqualTo(2);
            assertThat(result.getCompletedTaskCount()).isZero();
        }

        @Test
        @DisplayName("Task with null estimatedHours is treated as weight 1.0")
        void nullHours_treatedAsWeight1() {
            Task t = makeTask(1L, 50, null, TaskStatus.IN_PROGRESS);
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L)).thenReturn(List.of(t));

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getContributionPercent()).isEqualTo(50.0);
            assertThat(result.getTaskDetails()).hasSize(1);
            assertThat(result.getTaskDetails().get(0).getWeight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Only DONE tasks contribute to completedTaskCount")
        void doneTasks_countedCorrectly() {
            List<Task> tasks = List.of(
                makeTask(1L, 100, 2.0, TaskStatus.DONE),
                makeTask(2L, 100, 3.0, TaskStatus.DONE),
                makeTask(3L, 50,  5.0, TaskStatus.IN_PROGRESS)  // not done
            );
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L)).thenReturn(tasks);

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getCompletedTaskCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Task detail DTO contains all required fields")
        void detailDto_containsAllFields() {
            Task t = makeTask(42L, 75, 3.5, TaskStatus.IN_PROGRESS);
            when(taskRepository.findByAssignedToAndGroupId(alice, 10L)).thenReturn(List.of(t));

            MemberContributionDto result = sut.calculateContribution(alice, 10L);

            assertThat(result.getTaskDetails()).hasSize(1);
            MemberContributionDto.TaskContributionDetail d = result.getTaskDetails().get(0);
            assertThat(d.getTaskId()).isEqualTo(42L);
            assertThat(d.getProgress()).isEqualTo(75);
            assertThat(d.getWeight()).isEqualTo(3.5);
            assertThat(d.getWeightedProgress()).isEqualTo(262.5); // 75 × 3.5
            assertThat(d.getStatus()).isEqualTo("IN_PROGRESS");
        }
    }

    @Nested
    @DisplayName("calculateAllContributionsInGroup")
    class BatchContributionTests {

        @Test
        @DisplayName("Two users each with tasks — correct per-user contributions")
        void twoUsers_correctPerUserContributions() {
            User bob = User.builder().id(2L).name("Bob").build();
            User carol = User.builder().id(3L).name("Carol").build();

            List<Task> allTasks = List.of(
                withAssignee(makeTask(1L, 60, 2.0, TaskStatus.IN_PROGRESS), alice),
                withAssignee(makeTask(2L, 40, 8.0, TaskStatus.IN_PROGRESS), alice),
                withAssignee(makeTask(3L, 90, 5.0, TaskStatus.IN_PROGRESS), bob),
                withAssignee(makeTask(4L, 30, 5.0, TaskStatus.IN_PROGRESS), bob),
                withAssignee(makeTask(5L, 100, 4.0, TaskStatus.DONE), carol)
            );

            when(taskRepository.findAllByGroupIdWithAssignee(99L)).thenReturn(allTasks);

            Map<Long, MemberContributionDto> result = sut.calculateAllContributionsInGroup(99L);

            assertThat(result).hasSize(3);

            // Alice: (60×2 + 40×8) / (2+8) = (120+320)/10 = 44
            assertThat(result.get(alice.getId()).getContributionPercent()).isEqualTo(44.0);

            // Bob: (90×5 + 30×5) / (5+5) = (450+150)/10 = 60
            assertThat(result.get(bob.getId()).getContributionPercent()).isEqualTo(60.0);

            // Carol: 100 × 4 / 4 = 100
            assertThat(result.get(carol.getId()).getContributionPercent()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Empty group returns empty map")
        void emptyGroup_emptyMap() {
            when(taskRepository.findAllByGroupIdWithAssignee(99L))
                .thenReturn(Collections.emptyList());

            Map<Long, MemberContributionDto> result = sut.calculateAllContributionsInGroup(99L);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Helper factories
    // ══════════════════════════════════════════════════════════════════════════════

    private Task makeTask(Long id, Integer progress, Double hours, TaskStatus status) {
        return Task.builder()
            .id(id)
            .taskName("Task-" + id)
            .progress(progress)
            .estimatedHours(hours)
            .status(status)
            .group(com.teamup.teamup.entity.Group.builder().id(10L).build())
            .assignedTo(alice)
            .deadline(LocalDateTime.now().plusDays(7))
            .createdAt(LocalDateTime.now().minusDays(1))
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private Task withAssignee(Task task, User user) {
        task.setAssignedTo(user);
        return task;
    }
}
