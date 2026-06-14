package com.teamup.teamup.exception;

/**
 * Unified error response body returned by {@code GlobalExceptionHandler}
 * inside the {@code ApiResponse<T>} wrapper.
 */
public record ErrorDetails(
    String errorCode,
    String message,
    String path,
    int status
) {}
