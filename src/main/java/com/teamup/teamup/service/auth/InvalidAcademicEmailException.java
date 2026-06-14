package com.teamup.teamup.service.auth;

/**
 * Thrown by {@link com.teamup.teamup.service.auth.RegistrationService}
 * when a user attempts to register as ROLE_TEACHER with a non-academic email.
 *
 * Mapped by {@code GlobalExceptionHandler} to HTTP 403 Forbidden
 * with error code: {@code INVALID_ACADEMIC_EMAIL}.
 */
public class InvalidAcademicEmailException extends RuntimeException {

    public InvalidAcademicEmailException(String message) {
        super(message);
    }
}
