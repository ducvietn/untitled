package com.teamup.teamup.exception;

/**
 * Thrown when a user attempts to submit a second peer review for the same
 * reviewer → reviewee pair within a group.
 * Mapped to HTTP 409 Conflict with error code: {@code DUPLICATE_REVIEW}.
 */
public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException() {
        super("You have already submitted a review for this team member.");
    }
}
