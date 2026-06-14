package com.teamup.teamup.exception;

/**
 * Thrown when a peer review is submitted for two users who are not members
 * of the same group.
 * Mapped to HTTP 400 Bad Request with error code: {@code DIFFERENT_GROUP}.
 */
public class DifferentGroupException extends RuntimeException {

    public DifferentGroupException() {
        super("Both the reviewer and reviewee must belong to the same group.");
    }

    public DifferentGroupException(String message) {
        super(message);
    }
}
