package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Output of GroupProgressService — the absolute group completion percentage
 * plus a burndown time-series for chart rendering.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupProgressDto {

    /** The group's primary key. */
    private Long groupId;

    /** Human-readable group name. */
    private String groupName;

    /**
     * Absolute group completion % = (# DONE tasks / total tasks) × 100.
     * This differs from weighted average: it treats all tasks equally.
     */
    private Double absoluteCompletionPercent;

    /**
     * Weighted group completion % = sum(progress_i × weight_i) / sum(weight_i).
     * Weights each task by estimatedHours. More accurate for heterogeneous workloads.
     */
    private Double weightedCompletionPercent;

    /** Total number of tasks in the group. */
    private Integer totalTaskCount;

    /** Number of tasks with status = DONE. */
    private Integer doneTaskCount;

    /** Number of tasks IN_PROGRESS. */
    private Integer inProgressTaskCount;

    /** Number of tasks PENDING_REVIEW. */
    private Integer pendingReviewTaskCount;

    /** Number of tasks TO_DO. */
    private Integer todoTaskCount;

    /**
     * Project start date (earliest task.createdAt in the group).
     */
    private String projectStartDate;

    /**
     * Final deadline (latest task.deadline in the group).
     */
    private String finalDeadline;

    /**
     * Time-series data points for the burndown chart.
     * Each entry: { date, expected_progress, actual_progress }.
     */
    private List<BurndownPoint> burndownSeries;

    /**
     * A single data point in the burndown chart.
     * expected_progress = linear interpolation from 0% at start to 100% at deadline.
     * actual_progress  = group completion % as of that date.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BurndownPoint {
        /** ISO-8601 date string, e.g. "2024-11-01". */
        private String date;

        /**
         * Ideal (expected) cumulative progress % on this date.
         * Linearly scales from 0% on projectStartDate to 100% on finalDeadline.
         */
        private Double expectedProgress;

        /**
         * Actual cumulative progress % recorded on this date.
         * For dates in the future it is null.
         */
        private Double actualProgress;
    }
}
