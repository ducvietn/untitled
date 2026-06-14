package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Submission — a file uploaded for a Task.
 * Multiple submissions per task are allowed (e.g. re-submission after rejection).
 *
 * <pre>
 * FK index: idx_submissions_task_id  ON submissions(task_id)
 * </pre>
 *
 * The actual file bytes are stored on disk (or S3) under
 * {@code storage.upload-dir}. This entity stores only the relative URL path.
 */
@Entity
@Table(
    name = "submissions",
    indexes = {
        @Index(name = "idx_submissions_task_id", columnList = "task_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission extends BaseEntity implements AuditableEntity {

    /**
     * Relative path to the stored file, e.g. {@code submissions/task-42/report.pdf}.
     * Full URL is constructed as: {@code storage.upload-dir + "/" + fileUrl}.
     */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    /**
     * Original filename as uploaded by the user.
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * File size in bytes at time of upload.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME type detected from the uploaded file.
     */
    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    // ── Auditing fields ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationships ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "task_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_submissions_task")
    )
    private Task task;

    // ── Lifecycle callbacks ───────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }
}
