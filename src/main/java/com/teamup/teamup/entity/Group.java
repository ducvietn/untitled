package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Group — a team of students working on a shared project within a Class.
 *
 * <h3>Subject scoping</h3>
 * {@code subjectCode} and {@code subjectName} are mandatory and define the
 * domain boundary for teacher access. A teacher can only override groups
 * where {@code group.subjectCode == teacher.user.subjectCode}.
 *
 * <pre>
 * FK index: idx_groups_class_id       ON groups(class_id)
 * FK index: idx_groups_leader_id      ON groups(leader_id)
 * FK index: idx_groups_subject_code   ON groups(subject_code)
 * UK      : uk_groups_class_name     ON groups(class_id, group_name)
 * </pre>
 */
@Entity
@Table(
    name = "groups",
    indexes = {
        @Index(name = "idx_groups_class_id",     columnList = "class_id"),
        @Index(name = "idx_groups_leader_id",    columnList = "leader_id"),
        @Index(name = "idx_groups_subject_code",  columnList = "subject_code")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_groups_class_name",
            columnNames = {"class_id", "group_name"}
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group extends BaseEntity implements AuditableEntity {

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    // ── Subject scoping (mandatory) ─────────────────────────────────────────────

    /**
     * The subject domain this group belongs to.
     * Must match the teacher's {@link User#subjectCode} for override access.
     * Examples: "INT2204", "MATH101", "PHY1001"
     */
    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    /**
     * Human-readable subject name.
     * Examples: "Advanced Programming", "Calculus I", "Physics 101"
     */
    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    // ── Auditing fields ───────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "class_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_groups_class")
    )
    private Class classEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "leader_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_groups_leader")
    )
    private User leader;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "group_members",
        joinColumns = @JoinColumn(name = "group_id", foreignKey = @ForeignKey(name = "fk_gm_group")),
        inverseJoinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_gm_user"))
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Task> tasks = new HashSet<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<PeerReview> peerReviews = new HashSet<>();

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addMember(User user) {
        members.add(user);
        user.getGroups().add(this);
    }

    public void removeMember(User user) {
        members.remove(user);
        user.getGroups().remove(this);
    }

    public boolean isLeader(User user) {
        return this.leader != null && this.leader.getId().equals(user.getId());
    }
}
