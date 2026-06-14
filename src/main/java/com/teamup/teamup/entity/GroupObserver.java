package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Maps a read-only observer (TEACHING ASSISTANT, EXTERNAL MENTOR) to a specific group.
 *
 * <h3>Access model</h3>
 * A user with {@code ROLE_OBSERVER} may only read data within groups listed in this table
 * where {@code active = TRUE}. They receive all notifications triggered for that group
 * (task completion, frozen task alerts, peer review submissions).
 *
 * <h3>Soft-disable via {@code active}</h3>
 * Setting {@code active = FALSE} revokes observer access without deleting the record,
 * preserving the audit trail of who observed which group and when.
 *
 * <pre>
 * UK      : uk_group_observer    ON (group_id, user_id)
 * Index   : idx_group_observers_user  ON (user_id, active)
 * Index   : idx_group_observers_group ON (group_id, active)
 * </pre>
 */
@Entity
@Table(
    name = "group_observers",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_group_observer",
            columnNames = {"group_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_group_observers_user",
               columnList = "user_id, active"),
        @Index(name = "idx_group_observers_group",
               columnList = "group_id, active")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupObserver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_go_group"))
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_go_user"))
    private User user;

    @CreatedDate
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /**
     * Soft-disable flag. {@code FALSE} means the observer can no longer
     * access this group, but the historical record is preserved.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
