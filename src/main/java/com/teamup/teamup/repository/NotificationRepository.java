package com.teamup.teamup.repository;

import com.teamup.teamup.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for in-app notifications.
 *
 * All queries use the covering index
 * {@code idx_notifications_user_unread (user_id, is_read, created_at DESC)}
 * so no additional table lookups are needed for the paginated unread query.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Paginated list of UNREAD notifications for a user, newest first.
     * Covering index ensures this query is fully index-covered.
     *
     * @param userId   the notification receiver
     * @param pageable page + sort specification
     * @return page of unread notifications
     */
    Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(
            Long userId, Pageable pageable);

    /**
     * Paginated list of ALL notifications for a user (read + unread), newest first.
     *
     * @param userId   the notification receiver
     * @param pageable page + sort specification
     * @return page of all notifications
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Count of unread notifications — used for the bell icon badge.
     *
     * @param userId the notification receiver
     * @return number of unread notifications
     */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * Bulk mark all unread notifications as read for a given user.
     * Executed as a single UPDATE statement — O(1) database round-trip regardless
     * of how many notifications are marked.
     *
     * @param userId the notification receiver
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
           "WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);
}
