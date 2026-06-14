package com.teamup.teamup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting a peer review.
 *
 * Validation rules enforced at the controller/service layer:
 * - revieweeId ≠ reviewerId  (no self-review)
 * - both reviewer and reviewee must belong to the same group
 * - score ∈ [1, 5]
 * - comment: non-blank, max 2000 characters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerReviewSubmissionDto {

    /**
     * The group within which the review is submitted.
     */
    @NotNull(message = "Group ID is required.")
    private Long groupId;

    /**
     * The ID of the student being reviewed.
     */
    @NotNull(message = "Reviewee ID is required.")
    private Long revieweeId;

    /**
     * Attitude score: 1 (Very Poor) to 5 (Excellent).
     */
    @NotNull(message = "Score is required.")
    @Min(value = 1, message = "Score must be at least 1.")
    @Max(value = 5, message = "Score must be at most 5.")
    private Integer score;

    /**
     * Free-text comment supporting the score.
     */
    @NotBlank(message = "Comment is required.")
    private String comment;
}
