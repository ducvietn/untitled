package com.teamup.teamup.repository;

import com.teamup.teamup.entity.GroupObserver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for group-observer mappings.
 */
@Repository
public interface GroupObserverRepository extends JpaRepository<GroupObserver, Long> {

    /**
     * Returns all ACTIVE observer records for a group.
     * Used to send notifications to every observer of a group.
     *
     * @param groupId the group
     * @return list of active observer mappings
     */
    @Query("SELECT go FROM GroupObserver go " +
           "JOIN FETCH go.user " +
           "WHERE go.group.id = :groupId AND go.active = true")
    List<GroupObserver> findActiveObserversByGroupId(@Param("groupId") Long groupId);

    /**
     * Returns all ACTIVE group IDs that a given user observes.
     * Used to enforce observer read-access scope.
     *
     * @param userId the observer user
     * @return set of group IDs
     */
    @Query("SELECT go.group.id FROM GroupObserver go " +
           "WHERE go.user.id = :userId AND go.active = true")
    Set<Long> findActiveGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * Checks whether a given user is an active observer of a specific group.
     *
     * @param groupId the group
     * @param userId  the user
     * @return true if the user is an active observer of the group
     */
    boolean existsByGroupIdAndUserIdAndActiveTrue(Long groupId, Long userId);

    /**
     * Returns the specific mapping record.
     */
    Optional<GroupObserver> findByGroupIdAndUserId(Long groupId, Long userId);
}
