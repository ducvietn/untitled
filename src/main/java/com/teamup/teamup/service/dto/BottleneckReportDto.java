package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Output of BottleneckDetectionService — per-group bottleneck analysis
 * consumed by the Group Dashboard and God Eye Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BottleneckReportDto {

    private Long groupId;
    private String groupName;

    /**
     * True if at least one bottleneck task was detected.
     */
    private Boolean hasBottlenecks;

    /**
     * Bottleneck tasks ordered by severity (most blocking first).
     * Severity score = estimatedHours × (1 - progress/100).
     * Higher score = heavier and less-done = more blocking.
     */
    private List<BottleneckTaskDto> bottlenecks;

    /**
     * Total number of tasks currently blocking other tasks.
     */
    private Integer totalBlockingTaskCount;

    /**
     * A single bottleneck task entry.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BottleneckTaskDto {

        private Long taskId;
        private String taskName;
        private String assigneeName;
        private Long assigneeId;

        /**
         * Estimated hours — the "weight" that makes this task significant.
         */
        private Double estimatedHours;

        /**
         * Current progress 0–100.
         */
        private Integer progress;

        /**
         * Severity score: estimatedHours × (100 - progress) / 100.
         * Range: 0 (complete/light) → estimatedHours (heavy/unstarted).
         */
        private Double severityScore;

        /**
         * How many downstream tasks are blocked by this one.
         */
        private Integer blockedTaskCount;

        /**
         * IDs of the tasks blocked by this bottleneck.
         */
        private List<Long> blockedTaskIds;

        /**
         * Human-readable reason why this is flagged as a bottleneck.
         */
        private String reason;

        /**
         * Task deadline.
         */
        private String deadline;

        /**
         * True if this task's deadline has already passed.
         */
        private Boolean overdue;
    }
}
