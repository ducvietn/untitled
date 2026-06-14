package com.teamup.teamup.enums;

/**
 * Lifecycle state of a user account.
 *
 * <ul>
 *   <li>ACTIVE        — fully authenticated, can use the platform.</li>
 *   <li>PENDING_APPROVAL — teacher registration awaiting ROLE_ADMIN review.</li>
 *   <li>SUSPENDED      — temporarily disabled (admin action).</li>
 *   <li>REJECTED       — teacher registration denied.</li>
 * </ul>
 *
 * Only ACTIVE users can authenticate. All other states return 403.
 */
public enum AccountStatus {
    ACTIVE,
    PENDING_APPROVAL,
    SUSPENDED,
    REJECTED
}
