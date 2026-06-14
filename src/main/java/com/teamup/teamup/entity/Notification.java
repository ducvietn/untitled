package com.teamup.teamup.entity;

import com.teamup.teamup.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * In-app notification record.
 *
 * <h3>Delivery model</h3>
 * Notifications are <em>pull-based</em>: the frontend polls
 * {@code GET /api/notifications} and marks items as read via
 * {@code PUT /api/notifications/{id}/read}.
 *
 * <h3>No email</h3>
 * Email delivery is out of scope. All alerts stay strictly in-app.
 *
 * <pre>
 * Index: idx_notifications_user_unread ON (user_id, is_read, created_at DESC)
 * </pre>
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_user_unread",
               columnList = "user_id, is_read, created_at DESC")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who receives this notification.
     * Not nullable — a notification without a recipient is a dangling record.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private NotificationType type = NotificationType.APP_ALERT;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
