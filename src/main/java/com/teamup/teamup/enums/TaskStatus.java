package com.teamup.teamup.enums;

/**
 * Lifecycle of a Task within a Group.
 * Transitions:
 *   TO_DO  ──► IN_PROGRESS
 *   IN_PROGRESS ──► PENDING_REVIEW
 *   PENDING_REVIEW ──► DONE  (leader approves)
 *   PENDING_REVIEW ──► IN_PROGRESS  (leader rejects)
 *   DONE  ──► IN_PROGRESS  (only if grade is disputed / teacher resets)
 */
public enum TaskStatus {
    TO_DO,
    IN_PROGRESS,
    PENDING_REVIEW,
    DONE
}
