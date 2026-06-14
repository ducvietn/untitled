package com.teamup.teamup.repository;

import com.teamup.teamup.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Group entities.
 * Subject-scoped queries used by SubjectAuthorizationService.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Returns all groups with the given subject_code (indexed).
     * Used by TeacherController to filter the overview dashboard.
     */
    List<Group> findBySubjectCode(String subjectCode);

    /**
     * Returns true if a group with this subject_code exists.
     */
    boolean existsBySubjectCode(String subjectCode);

    /**
     * Returns all groups owned by a teacher (via the Class.owner relationship)
     * filtered to the teacher's subject_code.
     */
    @Query("""
        SELECT g FROM Group g
        JOIN FETCH g.classEntity c
        JOIN FETCH g.leader l
        WHERE c.owner.id = :teacherId
          AND g.subjectCode = :subjectCode
        ORDER BY g.groupName ASC
        """)
    List<Group> findByOwnerIdAndSubjectCode(
        @Param("teacherId") Long teacherId,
        @Param("subjectCode") String subjectCode
    );

    /**
     * Projection: fetches ONLY the subject_code of a group.
     * Used by SubjectAuthorizationService for O(1) scope checks.
     */
    @Query("SELECT g.subjectCode FROM Group g WHERE g.id = :groupId")
    Optional<String> findSubjectCodeById(@Param("groupId") Long groupId);
}
