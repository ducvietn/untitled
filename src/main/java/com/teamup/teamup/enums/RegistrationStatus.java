package com.teamup.teamup.enums;

/**
 * Status of a teacher registration request.
 *
 * <ul>
 *   <li>REGISTERING — teacher submitted the form; admin not yet reviewed.</li>
 *   <li>FAST_TRACK  — academic-domain email detected; shown first to admin.</li>
 *   <li>APPROVED    — admin accepted; user account activated.</li>
 *   <li>REJECTED    — admin denied; account not created.</li>
 * </ul>
 */
public enum RegistrationStatus {
    REGISTERING,
    FAST_TRACK,
    APPROVED,
    REJECTED
}
