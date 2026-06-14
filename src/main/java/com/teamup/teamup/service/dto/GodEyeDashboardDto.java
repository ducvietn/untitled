package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated result of GodEyeDashboardService — shows health of all groups
 * within a class and flags at-risk groups for teacher attention.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GodEyeDashboardDto {

    private Long classId;
    private String className;
    private String semester;

    /** Summary counts across all groups in the class. */
    private Summary summary;

    /** Per-group health cards. */
    private List<GroupHealthDto> groups;

    /** Groups that triggered at least one at-risk condition. */
    private List<GroupHealthDto> atRiskGroups;

    // ── Nested DTOs ────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private Integer totalGroups;
        private Integer healthyGroups;
        private Integer atRiskGroups;
        private Integer totalTasks;
        private Integer overdueTasks;
        private Integer staleTaskCount;       // tasks stalled > 72 h
        private Integer pendingReviewTasks;
        private Double averageGroupProgress;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupHealthDto {
        private Long groupId;
        private String groupName;
        private Long leaderId;
        private String leaderName;

        /**
         * Traffic-light health indicator.
         * GREEN  — on track, no warnings
         * YELLOW — lagging slightly OR has pending reviews
         * RED    — at-risk: &lt;30% avg progress with deadline &le; 2 days, OR multiple stale tasks
         */
        private HealthStatus healthStatus;

        private Double weightedProgressPercent;
        private Integer taskCount;
        private Integer doneTaskCount;
        private Integer staleTaskCount;
        private Integer overdueTaskCount;
        private Integer pendingReviewCount;

        /** Bottleneck summary (null if none detected). */
        private BottleneckReportDto bottleneckReport;

        /** Risk flags that drove the health status. */
        private List<String> riskFlags;

        public enum HealthStatus {
            GREEN, YELLOW, RED
        }
    }
}
