package com.teamup.teamup.repository;

import com.teamup.teamup.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entities.
 * All security-critical queries are read-only with transaction isolation.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by email (case-insensitive).
     * Used for login, registration duplicate-check, and JWT principal loading.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Returns true if a user with this email already exists.
     * Used as a fast pre-check before registration.
     */
    boolean existsByEmailIgnoreCase(String email);
}
