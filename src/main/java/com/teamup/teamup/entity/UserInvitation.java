package com.teamup.teamup.entity;

import com.teamup.teamup.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * UserInvitation — invite a student (existing or not-yet-registered) to a Class.
 *
 * Two paths:
 * <ol>
 *   <li>Existing user: invitation links directly to their user_id; auto-joins class on accept.</li>
 *   <li>New user: invitation token sent via email; account created on first login.</li>
 * </ol>
 *
 * <pre>
 * FK index: idx_invitations_class_id    ON user_invitations(class_id)
 * FK index: idx_invitations_email       ON user_invitations(email)
 * UK      : uk_invitation_token         ON user_invitations(token) — unique per active invite
 * </pre>
 */
@Entity
@Table(
    name = "user_invitations",
    indexes = {
        @Index(name = "idx_invitations_class_id", columnList = "class_id"),
        @Index(name = "idx_invitations_email",    columnList = "email")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_invitation_token", columnNames = "token")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInvitation extends BaseEntity implements AuditableEntity {

    /**
     * Cryptographically random token (UUID v4) — used in the invitation link.
     * Stored in plain text; compared using secure string comparison.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false, length = 255)
    private String email;

    /**
     * Name hint for pre-filling registration (for new students).
     */
    @Column(length = 100)
    private String studentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    /**
     * Invitation expires after this time (default: 7 days from creation).
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "class_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_invitations_class")
    )
    private Class classEntity;

    /**
     * Set when inviting an already-registered student (null for new students).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "invited_user_id",
        foreignKey = @ForeignKey(name = "fk_invitations_user")
    )
    private User invitedUser;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == InvitationStatus.PENDING && !isExpired();
    }
}
