package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Class — a course section managed by a TEACHER.
 *
 * <h3>Subject scoping</h3>
 * {@code subjectCode} and {@code subjectName} are mandatory.
 * The class's subject_code propagates to all its groups.
 *
 * <pre>
 * FK index: idx_classes_owner_id       ON classes(owner_id)
 * FK index: idx_classes_subject_code   ON classes(subject_code)
 * UK      : uk_classes_code           ON classes(class_code)
 * </pre>
 */
@Entity
@Table(
    name = "classes",
    indexes = {
        @Index(name = "idx_classes_owner_id",     columnList = "owner_id"),
        @Index(name = "idx_classes_subject_code", columnList = "subject_code")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_classes_code", columnNames = "class_code")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Class extends BaseEntity implements AuditableEntity {

    @Column(nullable = false, length = 150)
    private String className;

    /**
     * Machine-readable identifier (unique platform-wide).
     */
    @Column(name = "class_code", nullable = false, unique = true, length = 50)
    private String classCode;

    // ── Subject scoping (mandatory) ───────────────────────────────────────────

    /**
     * The subject domain this class belongs to.
     * Propagates to all groups within this class.
     * Must match the teacher's {@link User#subjectCode}.
     */
    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    /**
     * Human-readable subject name (e.g., "Advanced Programming").
     */
    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(length = 500)
    private String description;

    @Column(name = "semester", length = 30)
    private String semester;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "owner_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_classes_owner")
    )
    private User owner;

    @OneToMany(mappedBy = "classEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Group> groups = new HashSet<>();

    @OneToMany(mappedBy = "classEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserInvitation> invitations = new HashSet<>();
}
