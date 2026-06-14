package com.teamup.teamup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Full-detail DTO — used exclusively for ROLE_TEACHER and ROLE_ADMIN.
 * Contains ALL fields including reviewer identity.
 *
 * This DTO must NEVER be returned to a ROLE_STUDENT.
 * The ReviewController selects the appropriate DTO based on the caller's role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerReviewDetailDto {

    private Long reviewId;

    // ── Reviewer identity (NOT anonymised for teachers) ───────────────────────

    private Long reviewerId;
    private String reviewerName;
    private String reviewerEmail;

    // ── Reviewee identity (not needed for students, needed for teacher report) ─

    private Long revieweeId;
    private String revieweeName;

    // ── Review content ───────────────────────────────────────────────────────

    private Integer score;
    private String attitudeLabel;
    private String comment;
    private LocalDateTime reviewedAt;

    // ── Group reference ──────────────────────────────────────────────────────

    private Long groupId;
    private String groupName;
}
