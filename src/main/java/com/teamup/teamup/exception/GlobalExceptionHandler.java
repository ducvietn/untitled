package com.teamup.teamup.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;

import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 *
 * Maps domain exceptions → HTTP status + structured {@code ApiResponse} body.
 *
 * <h3>Error code reference</h3>
 * <table>
 *   <tr><th>Error Code</th><th>HTTP Status</th><th>Trigger</th></tr>
 *   <tr><td>RESOURCE_NOT_FOUND</td><td>404</td><td>Entity not found</td></tr>
 *   <tr><td>FILE_MISSING_FROM_STORAGE</td><td>404</td><td>Cloud file deleted but DB record exists</td></tr>
 *   <tr><td>CONCURRENT_MODIFICATION</td><td>409</td><td>Optimistic lock failure (stale @Version)</td></tr>
 *   <tr><td>VALIDATION_ERROR</td><td>400</td><td>Bean validation failure</td></tr>
 *   <tr><td>FILE_SIZE_EXCEEDED</td><td>413</td><td>Upload > 10 MB</td></tr>
 *   <tr><td>FILE_STORAGE_ERROR</td><td>500</td><td>Cloud SDK threw an exception</td></tr>
 *   <tr><td>INTERNAL_ERROR</td><td>500</td><td>Unexpected exception</td></tr>
 * </table>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404: Not Found ────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} [{}]", ex.getMessage(), request.getRequestURI());
        ErrorDetails details = new ErrorDetails(
            "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI(), 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(details));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileMissing(
            FileStorageException ex, HttpServletRequest request) {
        log.warn("File missing from storage: {} — {}", ex.getMessage(), request.getRequestURI());
        String code = "FILE_MISSING_FROM_STORAGE".equals(ex.getErrorCode())
            ? "FILE_MISSING_FROM_STORAGE"
            : "FILE_STORAGE_ERROR";
        ErrorDetails details = new ErrorDetails(
            code, ex.getMessage(), request.getRequestURI(), 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(details));
    }

    // ── 409: Conflict ─────────────────────────────────────────────────────────

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification detected: {} — {}", request.getRequestURI(), ex.getMessage());
        ErrorDetails details = new ErrorDetails(
            "CONCURRENT_MODIFICATION",
            "This record was modified by another user. Please refresh and try again.",
            request.getRequestURI(), 409);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(details));
    }

    // ── 400: Validation ───────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        log.warn("Validation failed: {} — {}", request.getRequestURI(), message);
        ErrorDetails details = new ErrorDetails(
            "VALIDATION_ERROR", message, request.getRequestURI(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    // ── 413: Payload too large ───────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload size exceeded: {} — {}", request.getRequestURI(), ex.getMessage());
        ErrorDetails details = new ErrorDetails(
            "FILE_SIZE_EXCEEDED",
            "Upload exceeds the maximum allowed file size of 10 MB.",
            request.getRequestURI(), 413);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(details));
    }

    // ── 403: Forbidden ─────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidAcademicEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleAcademicDomainViolation(
            InvalidAcademicEmailException ex, HttpServletRequest request) {
        log.warn("Non-academic teacher registration: {} — {}", request.getRequestURI(), ex.getMessage());
        ErrorDetails details = new ErrorDetails(
            "INVALID_ACADEMIC_EMAIL",
            ex.getMessage(),
            request.getRequestURI(), 403);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(details));
    }

    /**
     * HTTP 400: Teacher registered without providing subject_code.
     */
    @ExceptionHandler(MissingSubjectCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingSubjectCode(
            MissingSubjectCodeException ex, HttpServletRequest request) {
        log.warn("Missing subject code: {} — {}", request.getRequestURI(), ex.getMessage());
        ErrorDetails details = new ErrorDetails(
            "MISSING_SUBJECT_CODE",
            ex.getMessage(),
            request.getRequestURI(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} — {}", request.getRequestURI(), ex.getMessage());
        ErrorDetails details = new ErrorDetails(
            "ACCESS_DENIED",
            "You do not have permission to perform this action.",
            request.getRequestURI(), 403);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(details));
    }

    // ── Subject scope violations ────────────────────────────────────────────────

    /**
     * HTTP 403: Teacher attempted to access a group outside their subject domain.
     */
    @ExceptionHandler(SubjectAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubjectAccessDenied(
            SubjectAccessDeniedException ex, HttpServletRequest request) {
        log.warn("Subject access denied: teacher subject={}, requested={}",
            ex.getDeniedSubject(), request.getRequestURI());
        ErrorDetails details = new ErrorDetails(
            "SUBJECT_ACCESS_DENIED",
            ex.getMessage(),
            request.getRequestURI(), 403);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(details));
    }

    // ── Peer review validation ─────────────────────────────────────────────────

    @ExceptionHandler(SelfReviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleSelfReview(
            SelfReviewException ex, HttpServletRequest request) {
        ErrorDetails details = new ErrorDetails(
            "SELF_REVIEW_FORBIDDEN",
            ex.getMessage(),
            request.getRequestURI(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    @ExceptionHandler(DifferentGroupException.class)
    public ResponseEntity<ApiResponse<Void>> handleDifferentGroup(
            DifferentGroupException ex, HttpServletRequest request) {
        ErrorDetails details = new ErrorDetails(
            "DIFFERENT_GROUP",
            ex.getMessage(),
            request.getRequestURI(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateReview(
            DuplicateReviewException ex, HttpServletRequest request) {
        ErrorDetails details = new ErrorDetails(
            "DUPLICATE_REVIEW",
            ex.getMessage(),
            request.getRequestURI(), 409);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(details));
    }

    // ── 400: Illegal argument ───────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request: {} — {}", request.getRequestURI(), ex.getMessage());
        ErrorDetails details = new ErrorDetails(
            "BAD_REQUEST", ex.getMessage(), request.getRequestURI(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    // ── 500: Fallback ────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorDetails details = new ErrorDetails(
            "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI(), 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(details));
    }
}
