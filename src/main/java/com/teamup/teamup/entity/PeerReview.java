package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * PeerReview — a student's confidential assessment of a fellow group member.
 *
 * Business constraints (enforced at Service layer):
 * <ul>
 *   <li>A student cannot review themselves.</li>
 *   <li>A student can only review members of the same group.</li>
 *   <li>Only one review per reviewer → reviewee pair per group (unique constraint).</li>
 * </ul>
 *
 * <pre>
 * FK index: idx_peer_reviews_group_id     ON peer_reviews(group_id)
 * FK index: idx_peer_reviews_reviewer_id  ON peer_reviews(reviewer_id)
 * FK index: idx_peer_reviews_reviewee_id  ON peer_reviews(reviewee_id)
 * UK      : uk_peer_review_pair           ON (group_id, reviewer_id, reviewee_id)
 * </pre>
 */
@Entity
@Table(
    name = "peer_reviews",
    indexes = {
        @Index(name = "idx_peer_reviews_group_id",     columnList = "group_id"),
        @Index(name = "idx_peer_reviews_reviewer_id",  columnList = "reviewer_id"),
        @Index(name = "idx_peer_reviews_reviewee_id",  columnList = "reviewee_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_peer_review_pair",
            columnNames = {"group_id", "reviewer_id", "reviewee_id"}
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerReview extends BaseEntity implements AuditableEntity {

    /**
     * Rating from 1 (poor contribution) to 5 (exceptional contribution).
     */
    @Column(nullable = false)
    private Integer score;

    /**
     * Free-text comment supporting the score.
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    /**
     * The group within which this review is conducted.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "group_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_pr_group")
    )
    private Group group;

    /**
     * The student submitting the review.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewer_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_pr_reviewer")
    )
    private User reviewer;

    /**
     * The student being reviewed.
     * NOTE: This field is NOT exposed in any public DTO — it is used only
     * internally for aggregation. External reports will show only the
     * reviewee's anonymous ID or position.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewee_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_pr_reviewee")
    )
    private User reviewee;

    // ── Lifecycle callbacks ───────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (reviewedAt == null) reviewedAt = LocalDateTime.now();
        validateScore();
    }

    @PreUpdate
    protected void onUpdate() {
        validateScore();
    }

    /**
     * Business rule: score must be 1–5.
     */
    private void validateScore() {
        if (score == null || score < 1 || score > 5) {
            throw new IllegalStateException(
                "Peer review score must be between 1 and 5. Received: " + score);
        }
    }

    /**
     * Human-readable attitude label derived from the score.
     */
    public String attitudeLabel() {
        return switch (score) {
            case 1 -> "Very Poor";
            case 2 -> "Poor";
            case 3 -> "Satisfactory";
            case 4 -> "Good";
            case 5 -> "Excellent";
            default -> "Unknown";
        };
    }
}
