package com.teamup.teamup.controller;

import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.ApiResponse;
import com.teamup.teamup.service.dto.*;
import com.teamup.teamup.service.impl.FileManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for file management operations.
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th><th>Auth</th></tr>
 *   <tr><td>GET</td><td>/groups/{groupId}/files</td><td>Group Drive — all files in a group</td><td>Member of group</td></tr>
 *   <tr><td>GET</td><td>/files/download/{submissionId}</td><td>Get forced-download URL</td><td>Member of group</td></tr>
 *   <tr><td>POST</td><td>/tasks/{taskId}/upload</td><td>Upload a file to a task</td><td>Task assignee</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SubmissionController {

    private final FileManagementService fileService;

    // ═══════════════════════════════════════════════════════════════════════════════
    // Feature 12 — Group Drive
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/groups/{groupId}/files
     *
     * Returns all files submitted within a group — the "Group Drive".
     * Any group member can view this.
     *
     * @param groupId the group's primary key
     * @return GroupFileListDto — list of all submitted files with metadata
     */
    @GetMapping("/groups/{groupId}/files")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GroupFileListDto>> listGroupFiles(
            @PathVariable Long groupId,
            @AuthenticationPrincipal User currentUser) {

        log.info("Group Drive request: group={}, user={}", groupId, currentUser.getId());

        GroupFileListDto files = fileService.listGroupFiles(groupId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Feature 13 — Download
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/files/download/{submissionId}
     *
     * Returns a pre-signed / forced-download URL for a submission.
     * The frontend should redirect to this URL to trigger an auto-download.
     *
     * @param submissionId the submission's primary key
     * @return FileDownloadDto containing the download URL
     */
    @GetMapping("/files/download/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FileDownloadDto>> getDownloadUrl(
            @PathVariable Long submissionId) {

        log.info("Download URL request: submission={}", submissionId);

        FileDownloadDto dto = fileService.generateDownloadUrl(submissionId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Feature 13 — Upload
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/tasks/{taskId}/upload
     *
     * Uploads a file to a task. The file is sent to the cloud provider
     * (Cloudinary / S3) and the URL is stored in the Submission entity.
     *
     * Business rule enforced here (service layer):
     * - Extension whitelist (.pdf, .pptx, .docx, .zip)
     * - Max size 10 MB
     * - If task progress &lt; 100, this upload does NOT auto-set progress to 100
     *   (that is a separate workflow step)
     *
     * @param taskId        the task to upload to
     * @param file         the multipart file
     * @param currentUser  the authenticated uploader
     * @return UploadResultDto with the cloud URL and submission ID
     */
    @PostMapping(value = "/tasks/{taskId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UploadResultDto>> uploadFile(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        log.info("Upload request: task={}, file={}, user={}",
            taskId, file.getOriginalFilename(), currentUser.getId());

        UploadResultDto result = fileService.uploadFile(file, taskId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("File uploaded successfully.", result));
    }
}
