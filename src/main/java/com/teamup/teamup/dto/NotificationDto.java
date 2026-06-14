package com.teamup.teamup.dto;

import com.teamup.teamup.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a single notification.
 *
 * <h3>Read-only</h3>
 * This DTO has no setter — it is only used for API responses.
 */
@Data
@Builder
public class NotificationDto {

    private Long             id;
    private String          title;
    private String          message;
    private NotificationType type;
    private Boolean         isRead;
    private LocalDateTime   createdAt;
}
