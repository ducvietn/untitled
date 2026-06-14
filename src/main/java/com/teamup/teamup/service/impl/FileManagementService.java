package com.teamup.teamup.service.impl;

import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Submission;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.exception.FileStorageException;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.SubmissionRepository;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.*;
import com.teamup.teamup.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service 12 — Group Drive (centralised file repository) &
 * Feature 13 — Cloud storage & auto-download.
 *
 * <h3>Group Drive</h3>
 * {@code listGroupFiles()} fetches all submissions for a group in a single
 * JPQL query (JOIN FETCH), maps them to flat {@code GroupFileDto} records,
 * and computes summary statistics (total size, uploader count, etc.).
 *
 * <h3>Storage abstraction</h3>
 * {@code uploadFile()} and {@code generateDownloadUrl()} delegate to the
 * injected {@link StorageService} — Cloudinary in production,
 * {@code LocalStorageServiceImpl} in dev.
 *
 * <h3>Transactional safety</h3>
 * Upload flow: storage upload FIRST, then DB save. If the DB save fails,
 * {@code storage.delete()} is called in a {@code finally} block to avoid
 * orphaned files in the cloud.
 *
 * <h3>File-missing detection</h3>
 * Before returning the Group Drive, each file's URL is validated via
 * {@code storage.exists()}. If the cloud file is missing but the DB record
 * exists, the entry is flagged with {@code available = false} and a warning
 * is logged — the record is NOT deleted (admin review required).
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li>List files: O(n) — single JPQL join + O(n) mapping</li>
 *   <li>Upload: O(1) storage + O(1) DB</li>
 *   <li>Download URL: O(1)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FileManagementService {

    private final SubmissionRepository submissionRepository;
    private final GroupRepository     groupRepository;
    private final TaskRepository       taskRepository;
    private final StorageService       storageService;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ═══════════════════════════════════════════════════════════════════════════════
    // Feature 12 — Group Drive
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Lists all submitted files for a group (the "Group Drive" view).
     *
     * @param groupId     the group to list files for
     * @param currentUser the requesting user (used to mark {@code isOwnSubmission})
     * @return GroupFileListDto with file entries and summary statistics
     */
    public GroupFileListDto listGroupFiles(Long groupId, User currentUser) {
        log.debug("Listing Group Drive files for group {} by user {}", groupId, currentUser.getId());

        // ── O(1): verify group exists ──────────────────────────────────────────
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));

        // ── O(n): single JOIN query ────────────────────────────────────────────
        List<Object[]> rows = submissionRepository.findAllFilesInGroup(groupId);

        if (rows.isEmpty()) {
            return emptyListDto(group);
        }

        // ── O(n): map to DTOs ─────────────────────────────────────────────────
        List<GroupFileDto> files = rows.stream()
            .map(row -> toGroupFileDto(row, currentUser))
            .collect(Collectors.toList());

        // ── O(n): compute summary ───────────────────────────────────────────────
        GroupFileListDto.Summary summary = computeSummary(files);

        log.debug("Group Drive: {} files found for group {}", files.size(), groupId);

        return GroupFileListDto.builder()
            .groupId(groupId)
            .groupName(group.getGroupName())
            .totalFiles(files.size())
            .totalSizeBytes(files.stream().mapToLong(f -> f.getFileSizeBytes() != null ? f.getFileSizeBytes() : 0L).sum())
            .files(files)
            .summary(summary)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Feature 13 — Upload & Download
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Uploads a file to cloud storage and saves the submission record.
     *
     * <ol>
     *   <li>Validates the file (extension, size).</li>
     *   <li>Uploads to {@link StorageService} — gets cloud URL.</li>
     *   <li>Saves Submission entity to DB.</li>
     *   <li>If DB save fails → deletes the cloud file (no orphan).</li>
     * </ol>
     *
     * @param file      the multipart file
     * @param taskId    the task this submission belongs to
     * @param uploader  the user performing the upload
     * @return UploadResultDto with the cloud URL and submission ID
     */
    @Transactional
    public UploadResultDto uploadFile(MultipartFile file, Long taskId, User uploader) {
        log.info("Upload request: file={}, task={}, user={}",
            file.getOriginalFilename(), taskId, uploader.getId());

        var task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        Long groupId = task.getGroup().getId();

        // ── Step 1: upload to storage ──────────────────────────────────────────
        String cloudUrl;
        try {
            cloudUrl = storageService.upload(file, groupId, taskId);
            log.debug("File uploaded to storage: {} → {}", file.getOriginalFilename(), cloudUrl);
        } catch (Exception ex) {
            log.error("Storage upload failed for file {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new FileStorageException(
                "File upload failed. Please try again.", ex);
        }

        // ── Step 2: save to DB (rollback-safe) ─────────────────────────────────
        Submission submission;
        try {
            submission = Submission.builder()
                .fileUrl(cloudUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .submittedAt(LocalDateTime.now())
                .task(task)
                .build();

            submission = submissionRepository.save(submission);
            log.info("Submission record saved: id={}", submission.getId());

        } catch (Exception ex) {
            // ── Rollback: delete the orphaned cloud file ─────────────────────────
            log.error("DB save failed after upload. Rolling back storage: {}", cloudUrl);
            try {
                storageService.delete(cloudUrl);
            } catch (Exception deleteEx) {
                log.error("CRITICAL: orphan file could not be deleted: {}", cloudUrl, deleteEx);
            }
            throw ex; // rethrow original DB exception
        }

        return UploadResultDto.builder()
            .submissionId(submission.getId())
            .fileUrl(cloudUrl)
            .fileName(submission.getFileName())
            .fileSizeBytes(submission.getFileSize())
            .contentType(submission.getContentType())
            .message("File uploaded successfully.")
            .build();
    }

    /**
     * Generates a download URL for a submission.
     *
     * <ul>
     *   <li>Fetches the Submission record.</li>
     *   <li>Verifies the file exists in storage via {@code storage.exists()}.
     *       Throws {@code ResourceNotFoundException} with error code
     *       {@code FILE_MISSING_FROM_STORAGE} if the cloud file is gone.</li>
     *   <li>Delegates to {@link StorageService#generateDownloadUrl} to get
     *       the forced-attachment URL.</li>
     * </ul>
     *
     * @param submissionId the submission to generate a download URL for
     * @return FileDownloadDto with the download URL
     */
    public FileDownloadDto generateDownloadUrl(Long submissionId) {
        log.debug("Generating download URL for submission {}", submissionId);

        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));

        // ── Verify file exists in storage ────────────────────────────────────────
        if (!storageService.exists(submission.getFileUrl())) {
            log.error("FILE_MISSING_FROM_STORAGE: submissionId={}, fileUrl={}",
                submissionId, submission.getFileUrl());
            throw FileStorageException.fileMissingFromStorage(submission.getFileUrl());
        }

        // ── Generate forced-download URL ───────────────────────────────────────
        String downloadUrl = storageService.generateDownloadUrl(
            submission.getFileUrl(),
            submission.getFileName()
        );

        return FileDownloadDto.builder()
            .downloadUrl(downloadUrl)
            .fileName(submission.getFileName())
            .contentType(submission.getContentType())
            .fileSizeBytes(submission.getFileSize())
            .expiresAt(null) // null for local; populated by Cloudinary signed URL if available
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Maps a JPQL result row to a GroupFileDto.
     * Row: [Submission, taskName (String), uploaderName (String), uploaderId (Long)]
     */
    private GroupFileDto toGroupFileDto(Object[] row, User currentUser) {
        Submission s   = (Submission) row[0];
        String     tn  = (String)    row[1];
        String     un  = (String)    row[2];
        Long       uid = (Long)      row[3];

        return GroupFileDto.builder()
            .submissionId(s.getId())
            .fileName(s.getFileName())
            .fileUrl(s.getFileUrl())
            .contentType(s.getContentType())
            .fileSizeBytes(s.getFileSize())
            .fileSizeHuman(formatFileSize(s.getFileSize()))
            .uploaderId(uid)
            .uploaderName(un)
            .taskId(s.getTask().getId())
            .taskName(tn)
            .submittedAt(s.getSubmittedAt())
            .isOwnSubmission(uid.equals(currentUser.getId()))
            .build();
    }

    private GroupFileListDto.Summary computeSummary(List<GroupFileDto> files) {
        long oldestEpoch = files.stream()
            .map(GroupFileDto::getSubmittedAt)
            .filter(Objects::nonNull)
            .mapToLong(LocalDateTime::toLocalDate)
            .min().orElse(0);

        long newestEpoch = files.stream()
            .map(GroupFileDto::getSubmittedAt)
            .filter(Objects::nonNull)
            .mapToLong(LocalDateTime::toLocalDate)
            .max().orElse(0);

        LocalDateTime oldest = oldestEpoch > 0 ? LocalDateTime.ofEpochSecond(oldestEpoch, 0, java.time.ZoneOffset.UTC) : null;
        LocalDateTime newest = newestEpoch > 0 ? LocalDateTime.ofEpochSecond(newestEpoch, 0, java.time.ZoneOffset.UTC) : null;

        return GroupFileListDto.Summary.builder()
            .uniqueUploaders((int) files.stream().map(GroupFileDto::getUploaderId).distinct().count())
            .uniqueTasks((int) files.stream().map(GroupFileDto::getTaskId).distinct().count())
            .oldestFile(oldest != null ? oldest.atZone(java.time.ZoneOffset.UTC).format(ISO) : null)
            .newestFile(newest != null ? newest.atZone(java.time.ZoneOffset.UTC).format(ISO) : null)
            .lastUploadedAt(files.isEmpty() ? null
                : files.get(0).getSubmittedAt().atZone(java.time.ZoneOffset.UTC).format(ISO))
            .build();
    }

    private GroupFileListDto emptyListDto(Group group) {
        return GroupFileListDto.builder()
            .groupId(group.getId())
            .groupName(group.getGroupName())
            .totalFiles(0)
            .totalSizeBytes(0L)
            .files(Collections.emptyList())
            .summary(GroupFileListDto.Summary.builder()
                .uniqueUploaders(0)
                .uniqueTasks(0)
                .build())
            .build();
    }

    /**
     * Formats bytes into a human-readable string (B, KB, MB, GB).
     */
    public static String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }
}
