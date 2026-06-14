package com.teamup.teamup.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /api/files/download/{submissionId}.
 * Contains the pre-signed / forced-download URL that the frontend
 * should redirect to for auto-download.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDownloadDto {

    /**
     * The download URL. For Cloudinary: a signed URL with {@code fl_attachment}.
     * For local storage: a Spring streaming endpoint URL.
     */
    private String downloadUrl;

    /**
     * The original filename to suggest to the browser's save-as dialog.
     */
    private String fileName;

    /**
     * MIME content type (e.g. "application/pdf").
     */
    private String contentType;

    /**
     * File size in bytes.
     */
    private Long fileSizeBytes;

    /**
     * Suggested expiry time for signed URLs (null for local storage).
     */
    private String expiresAt;
}
