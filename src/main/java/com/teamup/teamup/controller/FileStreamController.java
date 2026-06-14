package com.teamup.teamup.controller;

import com.teamup.teamup.exception.ApiResponse;
import com.teamup.teamup.service.storage.LocalStorageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles file streaming for the local storage backend.
 *
 * This controller is only active when {@link LocalStorageServiceImpl} is in use
 * (i.e., in development environments without Cloudinary credentials).
 *
 * Production uses Cloudinary's CDN directly — this controller should be disabled
 * in prod via a {@code @Profile("!prod")} annotation (set at the application level
 * or on the bean).
 *
 * <h3>Security note</h3>
 * In a real deployment, this endpoint should verify that the requesting user
 * is a member of the group that owns the file. A file-access check using
 * {@code GroupMembershipValidator} must be added before uncommenting
 * {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileStreamController {

    private final LocalStorageServiceImpl localStorageService;

    /**
     * GET /api/files/stream?path={relativePath}&filename={displayName}
     *
     * Streams a file from the local upload directory with
     * {@code Content-Disposition: attachment} to force browser save-as.
     *
     * @param path     the relative storage path (e.g. "submissions/group-1/task-5/report.pdf")
     * @param filename the display name for the save-as dialog
     * @return the file as a binary stream
     */
    @GetMapping("/stream")
    // TODO: wire GroupMembershipValidator and replace with @PreAuthorize("...")
    // @PreAuthorize("@groupMembershipValidator.canAccessFile(#path, authentication.principal)")
    public ResponseEntity<Resource> streamFile(
            @RequestParam("path") String path,
            @RequestParam(value = "filename", required = false) String filename) {

        log.debug("File stream request: path={}, filename={}", path, filename);

        Resource resource = localStorageService.loadAsResource(path);
        String displayName = (filename != null && !filename.isBlank())
            ? filename
            : path.substring(path.lastIndexOf('/') + 1);

        String encoded = URLEncoder.encode(displayName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(resource);
    }
}
