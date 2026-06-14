package com.teamup.teamup.repository;

import com.teamup.teamup.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Submission entities.
 *
 * <h3>Group Drive query</h3>
 * {@code findAllFilesInGroup} JOINs tasks + users to produce a denormalised
 * projection — one round-trip to the DB regardless of how many submissions exist.
 *
 * <h3>Security note</h3>
 * The caller (service layer) is responsible for verifying that the requesting
 * user is a member of the group before calling these methods.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Returns all submissions for a group, enriched with uploader and task names.
     *
     * JPQL projection — Hibernate creates an optimised query without loading
     * the full entity graph.
     *
     * Results are ordered by submittedAt DESC so the frontend can display
     * newest files first.
     *
     * @param groupId the group's primary key
     * @return list of Object[]: [Submission, Task.taskName, User.name, User.id]
     */
    @Query("""
        SELECT s, t.taskName, u.name, u.id
        FROM Submission s
        JOIN s.task t
        JOIN t.assignedTo u
        WHERE t.group.id = :groupId
        ORDER BY s.submittedAt DESC
        """)
    List<Object[]> findAllFilesInGroup(@Param("groupId") Long groupId);

    /**
     * All submissions for a specific task.
     */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.task.id = :taskId
        ORDER BY s.submittedAt DESC
        """)
    List<Submission> findByTaskId(@Param("taskId") Long taskId);

    /**
     * All submissions for a group (all tasks).
     * Used by MasterOverrideService.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.task t
        WHERE t.group.id = :groupId
        ORDER BY s.submittedAt DESC
        """)
    List<Submission> findAllByGroupId(@Param("groupId") Long groupId);

    /**
     * Count submissions per group — used for file table pagination / summary.
     */
    @Query("""
        SELECT COUNT(s) FROM Submission s
        JOIN s.task t
        WHERE t.group.id = :groupId
        """)
    long countByGroupId(@Param("groupId") Long groupId);

    /**
     * Projection: fetches ONLY the group's primary key for a submission.
     * Navigates: submission → task → group.
     * Used by SubjectAuthorizationService for O(1) submission → group scope lookups.
     */
    @Query("""
        SELECT t.group.id
        FROM Submission s
        JOIN s.task t
        WHERE s.id = :submissionId
        """)
    java.util.Optional<Long> findTaskGroupIdBySubmissionId(@Param("submissionId") Long submissionId);
}
