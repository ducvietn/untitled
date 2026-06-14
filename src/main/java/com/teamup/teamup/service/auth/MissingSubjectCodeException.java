package com.teamup.teamup.service.auth;

/**
 * Thrown when a user registers as ROLE_TEACHER without providing a subject_code.
 *
 * Mapped by {@code GlobalExceptionHandler} to HTTP 400 Bad Request
 * with error code: {@code MISSING_SUBJECT_CODE}.
 */
public class MissingSubjectCodeException extends RuntimeException {

    public MissingSubjectCodeException(String message) {
        super(message);
    }
}
