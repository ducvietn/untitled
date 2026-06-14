package com.teamup.teamup.service;

import com.teamup.teamup.dto.NotificationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Notification service interface.
 */
public interface NotificationService {

    /**
     * Returns a paginated list of unread notifications for a user, newest first.
     *
     * @param userId   the notification receiver
     * @param pageable page number, size, and sort
     * @return page of unread notification DTOs
     */
    Page<NotificationDto> getUnreadNotifications(Long userId, Pageable pageable);

    /**
     * Returns a paginated list of ALL notifications (read + unread) for a user.
     *
     * @param userId   the notification receiver
     * @param pageable page number, size, and sort
     * @return page of all notification DTOs
     */
    Page<NotificationDto> getAllNotifications(Long userId, Pageable pageable);

    /**
     * Returns the count of unread notifications for the badge counter.
     *
     * @param userId the notification receiver
     * @return unread count
     */
    long countUnread(Long userId);

    /**
     * Marks a single notification as read.
     *
     * @param notificationId the notification to mark
     * @param userId        the requesting user (must be the owner)
     * @throws com.teamup.teamup.exception.ResourceNotFoundException if not found
     * @throws java.security.AccessDeniedException                  if userId does not own the notification
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * Marks all unread notifications as read for a user.
     *
     * @param userId the notification receiver
     * @return number of notifications marked as read
     */
    int markAllAsRead(Long userId);
}
