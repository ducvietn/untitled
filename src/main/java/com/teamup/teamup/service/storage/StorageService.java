package com.teamup.teamup.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Abstraction over file storage backends (Cloudinary, AWS S3, or local disk).
 *
 * All methods throw {@link com.teamup.teamup.exception.FileStorageException}
 * on I/O failures. The caller is responsible for transaction management
 * (the database save must be rolled back if storage upload fails).
 *
 * <h3>Design: Strategy Pattern</h3>
 * {@code CloudinaryStorageServiceImpl} and {@code LocalStorageServiceImpl}
 * both implement this interface. The active implementation is selected at
 * startup via the Spring profile or a {@code @ConditionalOnProperty} flag
 * in {@code CloudStorageConfig}.
 */
public interface StorageService {

    // ══════════════════════════════════════════════════════════════════════════════
    // Upload
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Uploads a file to the storage backend.
     *
     * @param file      the multipart file from the HTTP request
     * @param groupId   used to construct the storage path: {@code submissions/group-{id}/}
     * @param taskId    used to further namespace the file: {@code .../task-{tid}/}
     * @return the public or signed URL that can be used to serve the file
     * @throws com.teamup.teamup.exception.FileStorageException if upload fails
     */
    String upload(MultipartFile file, Long groupId, Long taskId);

    // ══════════════════════════════════════════════════════════════════════════════
    // Download
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Generates a <strong>download URL</strong> that forces the browser to
     * save the file as an attachment (Content-Disposition: attachment).
     *
     * <ul>
     *   <li>Cloudinary: appends {@code fl_attachment} to the existing resource URL.</li>
     *   <li>S3: generates a pre-signed URL with {@code ResponseContentDisposition}.</li>
     *   <li>Local: returns a Spring {@code /api/files/stream/{id}} redirect URL.</li>
     * </ul>
     *
     * @param fileUrl   the stored URL returned by {@link #upload}
     * @param fileName  the original filename — used to set the attachment filename
     * @return a URL string that triggers a forced download when opened in a browser
     */
    String generateDownloadUrl(String fileUrl, String fileName);

    // ══════════════════════════════════════════════════════════════════════════════
    // Deletion
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Deletes a file from the storage backend.
     *
     * @param fileUrl  the URL/path returned by {@link #upload}
     * @throws com.teamup.teamup.exception.FileStorageException if deletion fails
     */
    void delete(String fileUrl);

    // ══════════════════════════════════════════════════════════════════════════════
    // Validation
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Checks whether a file actually exists in the storage backend.
     * Used to distinguish between a valid DB record and a dangling reference
     * (file deleted from cloud but not cleaned up in DB).
     *
     * @param fileUrl the URL returned by {@link #upload}
     * @return true if the file exists and is accessible; false otherwise
     */
    boolean exists(String fileUrl);

    /**
     * Returns the list of allowed MIME types / extensions for upload validation.
     * Configured via {@code storage.allowed-extensions} in application.yml.
     */
    List<String> allowedExtensions();

    /**
     * Returns the maximum allowed file size in bytes.
     * Configured via {@code storage.max-file-size-mb} in application.yml.
     */
    long maxFileSizeBytes();
}
