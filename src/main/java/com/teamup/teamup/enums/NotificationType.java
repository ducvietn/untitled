package com.teamup.teamup.enums;

/**
 * Two notification types covering the full alert surface.
 *
 * <ul>
 *   <li>{@code APP_ALERT}       — standard in-app alerts surfaced in the Notice Board UI.</li>
 *   <li>{@code SYSTEM_WARNING}  — high-severity alerts (frozen tasks, deadline breaches) rendered
 *                                with a distinct amber/red visual treatment on the frontend.</li>
 * </ul>
 */
public enum NotificationType {
    /** Standard informational alert. */
    APP_ALERT,

    /** High-severity system warning (e.g. task frozen for 72 h, deadline exceeded). */
    SYSTEM_WARNING
}
