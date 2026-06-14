package com.teamup.teamup.enums;

/**
 * Four roles covering the full RBAC spectrum.
 *
 * <h3>Observer note</h3>
 * {@code ROLE_OBSERVER} is a read-only role assigned to specific groups via the
 * {@code group_observers} mapping table. An observer can read tasks, submissions,
 * and reports within the groups they are assigned to, but cannot modify any data.
 */
public enum Role {
    /** Regular student — can join groups, submit tasks, review peers. */
    ROLE_STUDENT,

    /** Verified educator — observer and report consumer. */
    ROLE_TEACHER,

    /** Platform administrator — approves or rejects teacher registrations. */
    ROLE_ADMIN,

    /**
     * Read-only observer (e.g. teaching assistant, external mentor).
     * Access is scoped to specific groups via {@code group_observers} mapping.
     */
    ROLE_OBSERVER
}
