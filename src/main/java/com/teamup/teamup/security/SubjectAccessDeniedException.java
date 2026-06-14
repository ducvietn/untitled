package com.teamup.teamup.security;

import lombok.Getter;

/**
 * Thrown by {@link SubjectAuthorizationService} when a teacher attempts to access
 * a group or subject domain that does not match their registered subject_code.
 *
 * Mapped by {@code GlobalExceptionHandler} to HTTP 403 Forbidden
 * with error code: {@code SUBJECT_ACCESS_DENIED}.
 *
 * The {@code deniedSubject} field carries the teacher's registered subject
 * for frontend display in the error message.
 */
@Getter
public class SubjectAccessDeniedException extends RuntimeException {

    private final String deniedSubject;
    private final String errorCode = "SUBJECT_ACCESS_DENIED";

    public SubjectAccessDeniedException(String message, String deniedSubject) {
        super(message);
        this.deniedSubject = deniedSubject;
    }

    public String getErrorCode() { return errorCode; }
}
