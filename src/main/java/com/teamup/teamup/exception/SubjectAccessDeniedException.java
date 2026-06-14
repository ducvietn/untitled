package com.teamup.teamup.exception;

public class SubjectAccessDeniedException extends RuntimeException {
    private final String deniedSubject;

    public SubjectAccessDeniedException(String subject, String message) {
        super(message);
        this.deniedSubject = subject;
    }

    public String getDeniedSubject() {
        return deniedSubject;
    }
}
