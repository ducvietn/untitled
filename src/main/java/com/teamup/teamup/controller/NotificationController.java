package com.teamup.teamup.controller;

import com.teamup.teamup.dto.NotificationDto;
import com.teamup.teamup.exception.ApiResponse;
import com.teamup.teamup.security.CustomUserDetails;
import com.teamup.teamup.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the in-app Notice Board.
 *
 * <h3>Feature 14 — Notice Board API</h3>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/notifications</td><td>Authenticated</td><td>Paginated unread notifications</td></tr>
 *   <tr><td>GET</td><td>/api/notifications/all</td><td>Authenticated</td><td>Paginated all notifications</td></tr>
 *   <tr><td>GET</td><td>/api/notifications/unread-count</td><td>Authenticated</td><td>Badge counter</td></tr>
 *   <tr><td>PUT</td><td>/api/notifications/{id}/read</td><td>Authenticated</td><td>Mark one as read</td></tr>
 *   <tr><td>PUT</td><td>/api/notifications/read-all</td><td>Authenticated</td><td>Mark all as read</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/notifications  — paginated unread notifications
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/notifications
     *
     * Returns unread notifications for the current user, paginated and sorted newest-first.
     *
     * @param page page number (0-based), default 0
     * @param size page size, default 20, max 100
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> getUnreadNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<NotificationDto> notifications =
            notificationService.getUnreadNotifications(currentUser.getUserId(), pageable);

        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/notifications/all  — paginated all notifications
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/notifications/all
     *
     * Returns ALL notifications (read + unread) for the current user.
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> getAllNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<NotificationDto> notifications =
            notificationService.getAllNotifications(currentUser.getUserId(), pageable);

        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/notifications/unread-count  — badge counter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/notifications/unread-count
     *
     * Returns the unread notification count — used for the notification bell badge.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        long count = notificationService.countUnread(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", count)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/notifications/{id}/read  — mark one as read
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PUT /api/notifications/{id}/read
     *
     * Marks a single notification as read.
     * Ownership is validated inside NotificationService to prevent IDOR.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        notificationService.markAsRead(id, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read.", null));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/notifications/read-all  — mark all as read
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PUT /api/notifications/read-all
     *
     * Marks all unread notifications as read for the current user.
     */
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        int updated = notificationService.markAllAsRead(currentUser.getUserId());
        return ResponseEntity.ok(
            ApiResponse.ok(Map.of("markedAsRead", updated)));
    }
}
