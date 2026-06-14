package com.teamup.teamup.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.teamup.teamup.exception.FileStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * Cloudinary implementation of {@link StorageService}.
 *
 * <h3>Upload behaviour</h3>
 * Files are uploaded to Cloudinary under the {@code submissions/{groupId}/task-{taskId}/}
 * folder. The returned URL is the secure Cloudinary delivery URL.
 *
 * <h3>Download behaviour</h3>
 * The {@code fl_attachment} transformation is appended to the delivery URL so that
 * opening the URL in a browser triggers a <strong>forced save-as</strong> dialog
 * with the correct original filename — no backend proxy is needed.
 *
 * <h3>Deletion</h3>
 * Files are deleted using the Cloudinary Admin API.
 * <strong>Note:</strong> Cloud name, API key, and API secret must be supplied
 * via environment variables (not committed to source code).
 *
 * <h3>Required environment variables</h3>
 * <pre>
 * CLOUDINARY_CLOUD_NAME=your-cloud-name
 * CLOUDINARY_API_KEY=your-api-key
 * CLOUDINARY_API_SECRET=your-api-secret
 * </pre>
 *
 * @see <a href="https://cloudinary.com/documentation/java_integration">Cloudinary Java SDK</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryStorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    @Value("${storage.allowed-extensions:pdf,pptx,docx,zip}")
    private List<String> allowedExtensions;

    @Value("${storage.max-file-size-mb:10}")
    private int maxFileSizeMb;

    private static final String FOLDER_TEMPLATE = "submissions/group-%d/task-%d";

    // ═══════════════════════════════════════════════════════════════════════════
    // Upload
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public String upload(MultipartFile file, Long groupId, Long taskId) {
        String folder = String.format(FOLDER_TEMPLATE, groupId, taskId);
        String publicId = generatePublicId(file.getOriginalFilename());

        log.debug("Uploading {} ({} bytes) to Cloudinary folder: {} / {}",
            file.getOriginalFilename(), file.getSize(), folder, publicId);

        Map<String, Object> params = ObjectUtils.asMap(
            "folder",           folder,
            "public_id",       publicId,
            "overwrite",       true,
            "resource_type",   "raw",          // non-image: pdf, zip, docx, pptx
            "use_filename",    true,
            "unique_filename", false,
            "access_mode",    "public"
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                file.getBytes(), params);

            String url = (String) result.get("secure_url");
            log.info("Cloudinary upload successful: {} → {}", file.getOriginalFilename(), url);
            return url;

        } catch (IOException ex) {
            log.error("Cloudinary upload failed for {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new FileStorageException(
                "Failed to upload file to Cloudinary: " + file.getOriginalFilename(), ex);
        } catch (Exception ex) {
            log.error("Cloudinary SDK error: {}", ex.getMessage(), ex);
            throw new FileStorageException(
                "Cloudinary SDK error during upload: " + ex.getMessage(), ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Download URL — fl_attachment forces browser save-as
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Cloudinary download URL: appends {@code fl_attachment} transformation
     * so the browser saves the file instead of displaying it inline.
     *
     * Pattern: {@code https://res.cloudinary.com/{cloud}/raw/upload/fl_attachment/{public_id}}
     *
     * The original filename is preserved via the download filename parameter.
     */
    @Override
    public String generateDownloadUrl(String fileUrl, String fileName) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("fileUrl must not be blank");
        }

        // Extract public_id from the Cloudinary URL
        // URL format: https://res.cloudinary.com/{cloud}/raw/upload/v{version}/{path}
        String publicId = extractPublicId(fileUrl);

        try {
            // Generate a signed URL with fl_attachment transformation
            @SuppressWarnings("unchecked")
            Map<String, String> signedUrl = cloudinary.url()
                .resourceType("raw")
                .transformation(
                    new com.cloudinary.Transformation()
                        .addVariable("fl_attachment", fileName)
                )
                .signed(true)
                .generate(publicId);

            log.debug("Generated signed download URL for public_id: {}", publicId);
            return signedUrl.get("url");

        } catch (Exception ex) {
            log.warn("Failed to generate signed URL for {}: falling back to public URL with fl_attachment",
                fileUrl);
            // Fallback: append fl_attachment directly to the delivery URL
            return appendFlAttachment(fileUrl, fileName);
        }
    }

    /**
     * Fallback: appends {@code fl_attachment} query parameter directly to the URL.
     * Works for public (non-signed) resources.
     */
    private String appendFlAttachment(String fileUrl, String fileName) {
        String separator = fileUrl.contains("?") ? "&" : "?";
        // URL-encode the filename for safety
        String encodedFilename = fileName.replace(" ", "%20");
        return fileUrl + separator + "fl_attachment=" + encodedFilename;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deletion
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void delete(String fileUrl) {
        String publicId = extractPublicId(fileUrl);
        log.debug("Deleting Cloudinary resource: {}", publicId);

        try {
            cloudinary.uploader().destroy(publicId,
                ObjectUtils.asMap("resource_type", "raw"));
            log.info("Cloudinary resource deleted: {}", publicId);
        } catch (Exception ex) {
            log.error("Failed to delete Cloudinary resource {}: {}", publicId, ex.getMessage());
            throw new FileStorageException(
                "Failed to delete file from Cloudinary: " + publicId, ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Existence check — ping the resource
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean exists(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return false;
        String publicId = extractPublicId(fileUrl);

        try {
            ApiResponse response = cloudinary.api().resource(publicId,
                ObjectUtils.asMap("resource_type", "raw", "quiet", true));
            return response != null;
        } catch (Exception ex) {
            log.debug("Cloudinary resource not found (exists=false): {}", publicId);
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Validation helpers
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public List<String> allowedExtensions() {
        return allowedExtensions;
    }

    @Override
    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Strips the file extension and appends a UUID suffix to guarantee uniqueness.
     * e.g. "report.pdf" → "report_a3f8c2d1"
     */
    private String generatePublicId(String originalFilename) {
        String base = originalFilename != null
            ? originalFilename.replaceFirst("\\.[^.]+$", "")  // strip extension
            : "file";
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Extracts the public_id from a Cloudinary delivery URL.
     *
     * Input:  {@code https://res.cloudinary.com/my-cloud/raw/upload/v1728000000/submissions/group-5/task-42/report.pdf}
     * Output: {@code submissions/group-5/task-42/report.pdf}
     */
    private String extractPublicId(String fileUrl) {
        // Remove the protocol, host, resource type, and version segment
        // Format: https://res.cloudinary.com/{cloud}/raw/upload/v{version}/{public_id}
        String stripped = fileUrl
            .replaceFirst("^https?://res\\.cloudinary\\.com/[^/]+/raw/upload/", "")
            .replaceFirst("^v\\d+/", "");               // strip version segment if present
        return URI.create(stripped).getPath();           // decode URI-encoded characters
    }
}
