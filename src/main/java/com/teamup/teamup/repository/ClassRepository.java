package com.teamup.teamup.repository;

import com.teamup.teamup.entity.Class;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Class entities.
 */
@Repository
public interface ClassRepository extends JpaRepository<Class, Long> {

    /**
     * Finds all classes owned by a specific teacher.
     * Used by TeacherController to build the overview.
     */
    List<Class> findByOwnerId(Long ownerId);

    /**
     * Finds all classes owned by a teacher filtered by subject_code.
     * A teacher only sees their own subject's classes.
     */
    List<Class> findByOwnerIdAndSubjectCode(Long ownerId, String subjectCode);

    /**
     * Finds a class by its machine-readable code.
     */
    Optional<Class> findByClassCode(String classCode);

    /**
     * Returns true if a class with this code already exists.
     */
    boolean existsByClassCode(String classCode);

    /**
     * Finds all classes owned by a teacher with groups eagerly loaded (avoids N+1).
     */
    @Query("""
        SELECT DISTINCT c FROM Class c
        LEFT JOIN FETCH c.groups g
        WHERE c.owner.id = :ownerId
        AND c.subjectCode = :subjectCode
        """)
    List<Class> findByOwnerIdAndSubjectCodeWithGroups(
        @Param("ownerId") Long ownerId,
        @Param("subjectCode") String subjectCode
    );
}
