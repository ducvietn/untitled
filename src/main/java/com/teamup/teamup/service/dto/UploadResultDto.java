package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned after a successful file upload.
 * Returned inside {@code ApiResponse<UploadResultDto>}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResultDto {

    private Long submissionId;
    private String fileUrl;         // cloud storage URL
    private String fileName;
    private Long fileSizeBytes;
    private String contentType;
    private String message;
}
