package com.teamup.teamup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Root DTO for the group report exported as Excel or PDF.
 * Aggregates three data sources:
 *   1. Member contribution % (from ProgressCalculationService)
 *   2. Submission log with ON_TIME / LATE flags (from SubmissionRepository)
 *   3. Average peer attitude scores (from PeerReviewRepository)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupReportDto {

    // ── Report metadata ─────────────────────────────────────────────────────────

    private Long groupId;
    private String groupName;
    private String subjectCode;
    private String subjectName;
    private String semester;

    // ── Summary table ─────────────────────────────────────────────────────────

    /** One row per member in the group. */
    private List<MemberReportRow> members;

    /** Group-level average peer attitude score. */
    private Double groupAverageAttitudeScore;

    /** Number of members with zero submissions. */
    private int inactiveMemberCount;

    /** Number of late submissions across the group. */
    private int totalLateSubmissions;

    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * One row in the summary table — one per group member.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberReportRow {

        /** Anonymous position number (1-based) used instead of name in reports. */
        private int memberPosition;

        /** Member's actual name (shown to teachers; replaced with position for students). */
        private String memberName;

        /** Weighted contribution % — from ProgressCalculationService. */
        private Double contributionPercent;

        /** Average peer attitude score (1–5) — rounded to 1 decimal. */
        private Double averageAttitudeScore;

        /** Human-readable attitude label (e.g. "Good", "Satisfactory"). */
        private String attitudeLabel;

        /** Total submissions made by this member. */
        private int totalSubmissions;

        /** Submissions that missed the task deadline. */
        private int lateSubmissions;

        /** Percentage of submissions that were on time. */
        private Double onTimePercent;
    }

    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Detailed log-work entry — one row per submission in chronological order.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionLogRow {

        /** The task this submission belongs to. */
        private String taskName;

        /** Who submitted it. */
        private String submitterName;

        /** When it was submitted. */
        private String submittedAt;

        /** Task's deadline. */
        private String taskDeadline;

        /** ON_TIME if submittedAt ≤ taskDeadline, LATE otherwise. */
        private SubmissionStatus status;

        /** File name. */
        private String fileName;

        /** File size in human-readable form. */
        private String fileSizeHuman;

        public enum SubmissionStatus {
            ON_TIME,
            LATE,
            /** For submissions that had no deadline (open-ended tasks). */
            NO_DEADLINE
        }
    }

    /** Chronological list of all submissions in the group. */
    private List<SubmissionLogRow> submissionLog;
}
