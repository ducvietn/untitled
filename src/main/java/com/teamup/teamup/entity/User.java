package com.teamup.teamup.entity;

import com.teamup.teamup.enums.AccountStatus;
import com.teamup.teamup.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User — represents STUDENT, TEACHER, and ADMIN actors.
 *
 * <h3>Subject scoping for teachers</h3>
 * {@code subjectCode} is mandatory for ROLE_TEACHER and MUST be provided
 * during registration. It defines the domain boundary for all teacher
 * override operations. Students have a null subject_code.
 *
 * <pre>
 * FK index: idx_users_email           ON users(email)
 * FK index: idx_users_role            ON users(role)
 * FK index: idx_users_account_status  ON users(account_status)
 * FK index: idx_users_subject_code    ON users(subject_code)
 * </pre>
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email",          columnList = "email"),
        @Index(name = "idx_users_role",           columnList = "role"),
        @Index(name = "idx_users_account_status", columnList = "account_status"),
        @Index(name = "idx_users_subject_code",   columnList = "subject_code")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity implements AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * Account lifecycle state. Only ACTIVE users may log in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // ── Subject scoping ──────────────────────────────────────────────────────

    /**
     * The subject domain this teacher is registered for.
     * MUST be non-null and non-blank for ROLE_TEACHER (enforced at registration).
     * Remains NULL for ROLE_STUDENT.
     *
     * Examples: "INT2204", "MATH101", "PHY1001"
     */
    @Column(name = "subject_code", length = 20)
    private String subjectCode;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "leader", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Group> ledGroups = new HashSet<>();

    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Group> groups = new HashSet<>();

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Class> ownedClasses = new HashSet<>();

    @OneToOne(mappedBy = "requester", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RegistrationRequest registrationRequest;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /**
     * True only for an ACTIVE user with ROLE_TEACHER.
     */
    public boolean isTeacher() {
        return role == Role.ROLE_TEACHER && isActive();
    }

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }

    /**
     * Returns true if this user is a teacher and their subject_code matches
     * the given code. Used by {@link com.teamup.teamup.security.SubjectAuthorizationService}.
     */
    public boolean teachesSubject(String code) {
        return role == Role.ROLE_TEACHER
            && subjectCode != null
            && subjectCode.equalsIgnoreCase(code);
    }
}
