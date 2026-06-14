package com.teamup.teamup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ANONYMOUS DTO — used when returning peer reviews to ROLE_STUDENT.
 *
 * This DTO intentionally omits ALL reviewer identity fields.
 * The reviewee sees ONLY their own score and comment — never who gave the review.
 *
 * This is enforced at the service/controller layer, NOT the frontend.
 * Even if a malicious actor intercepts the HTTP response, no reviewer
 * identity information is present in the payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnonymousReviewDto {

    /**
     * The reviewed student's group member position (1-based index).
     * Used only in the final report for anonymous identification.
     */
    private Integer memberPosition;

    /**
     * The score given by the anonymous reviewer.
     */
    private Integer score;

    /**
     * Human-readable attitude label derived from the score.
     * Examples: "Excellent", "Satisfactory", "Very Poor"
     */
    private String attitudeLabel;

    /**
     * The reviewer's free-text comment.
     * This is safe to expose because the reviewer is anonymous.
     */
    private String comment;

    /**
     * Timestamp when the review was submitted.
     */
    private LocalDateTime reviewedAt;
}
