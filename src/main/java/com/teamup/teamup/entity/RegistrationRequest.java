package com.teamup.teamup.entity;

import com.teamup.teamup.enums.RegistrationStatus;
import com.teamup.teamup.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * RegistrationRequest — captures a ROLE_TEACHER registration for ROLE_ADMIN review.
 *
 * One-to-one with User. Created when a teacher submits the registration form.
 * Deleted when the admin approves (User becomes ACTIVE) or rejects (User deleted).
 *
 * <pre>
 * FK index: idx_reg_requests_requester_id  ON registration_requests(requester_id)
 * UK      : uk_reg_request_email           ON registration_requests(email)
 * </pre>
 */
@Entity
@Table(
    name = "registration_requests",
    indexes = {
        @Index(name = "idx_reg_requests_requester_id", columnList = "requester_id"),
        @Index(name = "idx_reg_requests_status",       columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_reg_request_email", columnNames = "email")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationRequest extends BaseEntity implements AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Hashed password set during registration; promoted to User.password_hash on approval.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Always ROLE_TEACHER — this entity type exists only for teacher onboarding.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.ROLE_TEACHER;

    /**
     * Short bio or institutional affiliation submitted by the teacher.
     */
    @Column(length = 500)
    private String institution;

    // ── Approval fields ──────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.REGISTERING;

    /**
     * True when the email domain matches an academic pattern (@*.edu.vn etc.).
     * Fast-track requests appear at the top of the admin queue.
     */
    @Column(name = "fast_track", nullable = false)
    @Builder.Default
    private Boolean fastTrack = false;

    /**
     * Which admin reviewed (or rejected) this request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewed_by",
        foreignKey = @ForeignKey(name = "fk_reg_requests_reviewed_by")
    )
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(length = 255)
    private String rejectionReason;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    /**
     * The User entity that this request will become on approval.
     * Set immediately on registration; nulled (or entity deleted) on rejection.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "requester_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reg_requests_requester")
    )
    private User requester;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isFastTrack() {
        return Boolean.TRUE.equals(fastTrack);
    }

    public boolean isPending() {
        return status == RegistrationStatus.REGISTERING || status == RegistrationStatus.FAST_TRACK;
    }
}
