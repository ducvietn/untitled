package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single file entry in the Group Drive response.
 * Derived from submissions + task + user JOIN in SubmissionRepository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupFileDto {

    private Long submissionId;
    private String fileName;
    private String fileUrl;          // cloud or local path
    private String contentType;       // MIME type
    private Long fileSizeBytes;

    /** The student who uploaded this file. */
    private Long uploaderId;
    private String uploaderName;

    /** Which task this submission belongs to. */
    private Long taskId;
    private String taskName;

    /** When the submission was made. */
    private LocalDateTime submittedAt;

    /** Human-readable file size (e.g. "2.4 MB"). */
    private String fileSizeHuman;

    /** Convenience: true if the current user owns this submission. */
    private Boolean isOwnSubmission;
}
