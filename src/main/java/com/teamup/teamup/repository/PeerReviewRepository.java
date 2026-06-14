package com.teamup.teamup.repository;

import com.teamup.teamup.entity.PeerReview;
import com.teamup.teamup.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PeerReview entities.
 *
 * All queries use indexed FK columns. The unique constraint on
 * (group_id, reviewer_id, reviewee_id) guarantees at most one review
 * per pair per group.
 */
@Repository
public interface PeerReviewRepository extends JpaRepository<PeerReview, Long> {

    /**
     * Returns true if a review already exists for this reviewer → reviewee pair
     * within the given group.
     */
    @Query("""
        SELECT COUNT(pr) > 0
        FROM PeerReview pr
        WHERE pr.group.id       = :groupId
          AND pr.reviewer.id    = :reviewerId
          AND pr.reviewee.id    = :revieweeId
        """)
    boolean existsByGroupAndReviewerAndReviewee(
        @Param("groupId") Long groupId,
        @Param("reviewerId") Long reviewerId,
        @Param("revieweeId") Long revieweeId);

    /**
     * All reviews written BY a specific user within a group (used for teacher audit).
     */
    List<PeerReview> findByGroupIdAndReviewerId(Long groupId, Long reviewerId);

    /**
     * All reviews RECEIVED BY a specific user within a group.
     * Used when computing the average attitude score per member.
     */
    List<PeerReview> findByGroupIdAndRevieweeId(Long groupId, Long revieweeId);

    /**
     * Average score given TO a specific member in a group.
     * Returns null if no reviews exist for that member.
     */
    @Query("""
        SELECT AVG(pr.score)
        FROM PeerReview pr
        WHERE pr.group.id    = :groupId
          AND pr.reviewee.id = :revieweeId
        """)
    Double averageScoreForMember(
        @Param("groupId") Long groupId,
        @Param("revieweeId") Long revieweeId);

    /**
     * All reviews for a group (teacher-only endpoint).
     */
    @Query("""
        SELECT pr FROM PeerReview pr
        JOIN FETCH pr.reviewer r
        JOIN FETCH pr.reviewee e
        JOIN FETCH pr.group g
        WHERE pr.group.id = :groupId
        ORDER BY pr.reviewee.id, pr.reviewedAt ASC
        """)
    List<PeerReview> findAllByGroupIdWithUsers(@Param("groupId") Long groupId);

    /**
     * All reviews for a group where a specific user is the reviewee.
     * Used to show a student their received reviews (anonymised).
     */
    @Query("""
        SELECT pr FROM PeerReview pr
        WHERE pr.group.id    = :groupId
          AND pr.reviewee.id = :revieweeId
        ORDER BY pr.reviewedAt DESC
        """)
    List<PeerReview> findReceivedReviews(
        @Param("groupId") Long groupId,
        @Param("revieweeId") Long revieweeId);

    /**
     * Count of reviews submitted by a member within a group.
     * Used to enforce that all cross-pairs are reviewed.
     */
    long countByGroupIdAndReviewerId(Long groupId, Long reviewerId);
}
