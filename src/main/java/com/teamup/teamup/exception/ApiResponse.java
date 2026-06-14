package com.teamup.teamup.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Standardised API response wrapper.
 * Every REST endpoint must return this (or a subclass) as its top-level body.
 *
 * <pre>
 * {
 *   "status":  "OK" | "ERROR",
 *   "message": "Human-readable description",
 *   "data":    { ... },          // null on error
 *   "errors":  [...]             // null on success; populated on validation error
 * }
 * </pre>
 *
 * @param <T> the type of the payload on success
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    public enum Status { OK, ERROR }

    private Status status;
    private String message;
    private T data;
    private ErrorDetails error;

    // ── Static factory: success ───────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
            .status(Status.OK)
            .message("Success")
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
            .status(Status.OK)
            .message(message)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
            .status(Status.OK)
            .message(message)
            .build();
    }

    // ── Static factory: error ────────────────────────────────────────────────

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .status(Status.ERROR)
            .message(message)
            .build();
    }

    public static <T> ApiResponse<T> error(ErrorDetails details) {
        return ApiResponse.<T>builder()
            .status(Status.ERROR)
            .message(details.message())
            .error(details)
            .build();
    }
}
