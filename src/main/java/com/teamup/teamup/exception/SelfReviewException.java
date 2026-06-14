package com.teamup.teamup.exception;

/**
 * Thrown when a user attempts to submit a peer review for themselves.
 * Mapped to HTTP 400 Bad Request with error code: {@code SELF_REVIEW_FORBIDDEN}.
 */
public class SelfReviewException extends RuntimeException {

    public SelfReviewException() {
        super("You cannot submit a review for yourself.");
    }

    public SelfReviewException(String message) {
        super(message);
    }
}
