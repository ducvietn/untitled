package com.teamup.teamup.service.impl;

import com.teamup.teamup.dto.NotificationDto;
import com.teamup.teamup.entity.Notification;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.repository.NotificationRepository;
import com.teamup.teamup.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link NotificationService}.
 *
 * <h3>Ownership enforcement</h3>
 * Every read, mark-as-read, and mark-all-read operation validates that
 * the requesting user owns the notification. This prevents Student A from
 * manipulating Student B's notification state via ID spoofing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public Page<NotificationDto> getUnreadNotifications(Long userId, Pageable pageable) {
        return notificationRepository
            .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
            .map(this::toDto);
    }

    @Override
    public Page<NotificationDto> getAllNotifications(Long userId, Pageable pageable) {
        return notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toDto);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        // ── IDOR check: only the owner can mark their own notification as read ──
        if (!notification.getUser().getId().equals(userId)) {
            log.warn("IDOR attempt: user {} tried to mark notification {} owned by user {}",
                userId, notificationId, notification.getUser().getId());
            throw new AccessDeniedException(
                "You do not have permission to modify this notification.");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marked as read by user {}", notificationId, userId);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId);
        log.info("Marked {} notifications as read for user {}", updated, userId);
        return updated;
    }

    // ── Mapper ──────────────────────────────────────────────────────────────────

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
            .id(notification.getId())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .type(notification.getType())
            .isRead(notification.getIsRead())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
