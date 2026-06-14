package com.teamup.teamup.enums;

/**
 * Status of a student invitation to join a Class.
 *
 * <ul>
 *   <li>PENDING     — invitation sent; student has not acted.</li>
 *   <li>ACCEPTED    — student accepted and was added to the class roster.</li>
 *   <li>EXPIRED     — invitation exceeded its validity window (7 days default).</li>
 *   <li>CANCELLED   — teacher cancelled the invitation.</li>
 * </ul>
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    CANCELLED
}
