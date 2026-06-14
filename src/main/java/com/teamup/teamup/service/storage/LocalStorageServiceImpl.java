package com.teamup.teamup.service.storage;

import com.teamup.teamup.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Local-disk fallback implementation of {@link StorageService}.
 *
 * <strong>Use only in development / test environments.</strong>
 * Production must use {@link CloudinaryStorageServiceImpl} (or S3).
 *
 * <h3>Storage layout</h3>
 * {@code <upload-dir>/submissions/group-<id>/task-<tid>/<uuid>-<filename>}
 *
 * <h3>Download behaviour</h3>
 * Returns a relative path prefixed with {@code /api/files/stream/}.
 * A separate {@code FileStreamController} (wired separately) handles the
 * actual HTTP streaming with {@code Content-Disposition: attachment}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalStorageServiceImpl implements StorageService {

    @Value("${storage.upload-dir:./storage/uploads}")
    private String uploadDir;

    @Value("${storage.allowed-extensions:pdf,pptx,docx,zip}")
    private List<String> allowedExtensions;

    @Value("${storage.max-file-size-mb:10}")
    private int maxFileSizeMb;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
            log.info("LocalStorage initialised at: {}", rootLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create upload directory: " + rootLocation, ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Upload
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public String upload(MultipartFile file, Long groupId, Long taskId) {
        validateFile(file);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String storedFilename   = UUID.randomUUID() + "_" + originalFilename;
        Path  targetDir  = rootLocation.resolve(
            "submissions/group-" + groupId + "/task-" + taskId);
        Path  targetFile = targetDir.resolve(storedFilename);

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved locally: {}", targetFile);

            // Return a relative path that FileStreamController can resolve
            // Format: submissions/group-{id}/task-{tid}/{uuid}_{filename}
            return "submissions/group-" + groupId + "/task-" + taskId + "/" + storedFilename;

        } catch (IOException ex) {
            log.error("Failed to write file to disk: {}", targetFile, ex);
            throw new FileStorageException("Failed to store file: " + originalFilename, ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Download URL — returns an internal streaming endpoint
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the relative path as a streaming endpoint URL.
     * The {@code FileStreamController} will serve the file with
     * {@code Content-Disposition: attachment} headers.
     */
    @Override
    public String generateDownloadUrl(String fileUrl, String fileName) {
        return "/api/files/stream?path=" + fileUrl + "&filename=" + sanitizeFilename(fileName);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deletion
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void delete(String fileUrl) {
        Path target = rootLocation.resolve(fileUrl).normalize();
        // Prevent path traversal: resolved path must still be under rootLocation
        if (!target.startsWith(rootLocation)) {
            throw new FileStorageException("Invalid file path: " + fileUrl);
        }
        try {
            Files.deleteIfExists(target);
            log.info("Local file deleted: {}", target);
        } catch (IOException ex) {
            log.warn("Failed to delete local file {}: {}", target, ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Existence check
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean exists(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return false;
        Path target = rootLocation.resolve(fileUrl).normalize();
        return target.startsWith(rootLocation) && Files.exists(target);
    }

    /**
     * Exposes the file as a Spring {@link Resource} for streaming.
     * Used by {@code FileStreamController}.
     */
    public Resource loadAsResource(String fileUrl) {
        Path target = rootLocation.resolve(fileUrl).normalize();
        if (!target.startsWith(rootLocation)) {
            throw new FileStorageException("Invalid file path: " + fileUrl);
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new FileStorageException("Could not read file: " + fileUrl);
        } catch (MalformedURLException ex) {
            throw new FileStorageException("Could not read file: " + fileUrl, ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation helpers
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<String> allowedExtensions() {
        return allowedExtensions;
    }

    @Override
    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store empty file.");
        }
        String ext = getFileExtension(file.getOriginalFilename());
        if (!allowedExtensions.contains(ext.toLowerCase())) {
            throw new FileStorageException(
                "File type ." + ext + " is not allowed. Allowed: " + allowedExtensions);
        }
        if (file.getSize() > maxFileSizeBytes()) {
            throw new FileStorageException(
                "File size " + file.getSize() + " exceeds limit of " + maxFileSizeBytes() + " bytes.");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String sanitizeFilename(String filename) {
        // Remove path traversal characters
        if (filename == null) return "unknown";
        return filename.replaceAll("[/\\\\]", "_").replaceAll("\\.\\.", "");
    }
}
