package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Contribution data for a single member of a group.
 * Returned by ProgressCalculationService and consumed by the Group Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberContributionDto {

    /** Primary key of the user. */
    private Long userId;

    /** Display name of the user. */
    private String userName;

    /**
     * Weighted contribution percentage (0.0 – 100.0).
     * Formula: sum(progress_i × weight_i) / sum(weight_i)
     */
    private Double contributionPercent;

    /**
     * Number of tasks assigned to this user within the group.
     */
    private Integer taskCount;

    /**
     * Number of those tasks that are fully DONE.
     */
    private Integer completedTaskCount;

    /**
     * Per-task breakdown used by the drill-down modal.
     * Null in summary views; populated in detail requests.
     */
    private List<TaskContributionDetail> taskDetails;

    /**
     * Individual task-level contribution detail.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskContributionDetail {
        private Long taskId;
        private String taskName;
        private Integer progress;
        private Double weight;
        private Double weightedProgress;   // progress × weight
        private String status;
        private String deadline;
    }
}
