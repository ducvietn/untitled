package com.teamup.teamup.service;

import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Submission;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.exception.FileStorageException;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.SubmissionRepository;
import com.teamup.teamup.repository.TaskRepository;
import com.teamup.teamup.service.dto.*;
import com.teamup.teamup.service.impl.FileManagementService;
import com.teamup.teamup.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileManagementService.
 * Storage interactions are mocked; only service logic is exercised.
 */
@ExtendWith(MockitoExtension.class)
class FileManagementServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private GroupRepository     groupRepository;

    @Mock
    private TaskRepository      taskRepository;

    @Mock
    private StorageService      storageService;

    @InjectMocks
    private FileManagementService sut;

    private Group group;
    private User alice;
    private Task task;

    @BeforeEach
    void setUp() {
        group = Group.builder().id(1L).groupName("Team Alpha").build();
        alice = User.builder().id(10L).name("Alice").email("alice@test.com").build();
        task  = Task.builder()
            .id(5L)
            .taskName("Design Doc")
            .group(group)
            .assignedTo(alice)
            .deadline(LocalDateTime.now().plusDays(7))
            .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // listGroupFiles
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("listGroupFiles — Group Drive")
    class ListGroupFilesTests {

        @Test
        @DisplayName("Returns empty list when group has no submissions")
        void emptyGroup_emptyList() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(Collections.emptyList());

            GroupFileListDto result = sut.listGroupFiles(1L, alice);

            assertThat(result.getGroupId()).isEqualTo(1L);
            assertThat(result.getGroupName()).isEqualTo("Team Alpha");
            assertThat(result.getFiles()).isEmpty();
            assertThat(result.getTotalFiles()).isZero();
            assertThat(result.getTotalSizeBytes()).isZero();
            assertThat(result.getSummary().getUniqueUploaders()).isZero();
        }

        @Test
        @DisplayName("Returns files with correct metadata")
        void filesPresent_correctMetadata() {
            Submission s = makeSubmission(100L, "report.pdf", 204800L);
            Object[] row = new Object[]{ s, "Design Doc", "Alice", 10L };

            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of(row));

            GroupFileListDto result = sut.listGroupFiles(1L, alice);

            assertThat(result.getFiles()).hasSize(1);
            GroupFileDto file = result.getFiles().get(0);
            assertThat(file.getFileName()).isEqualTo("report.pdf");
            assertThat(file.getUploaderName()).isEqualTo("Alice");
            assertThat(file.getUploaderId()).isEqualTo(10L);
            assertThat(file.getTaskName()).isEqualTo("Design Doc");
            assertThat(file.getFileSizeHuman()).isEqualTo("200.0 KB");
            assertThat(file.getIsOwnSubmission()).isTrue(); // alice = uploader
        }

        @Test
        @DisplayName("isOwnSubmission is false when uploader is not current user")
        void notOwnSubmission_false() {
            User bob = User.builder().id(99L).name("Bob").build();
            Submission s = makeSubmission(100L, "bob-doc.pdf", 50000L);
            Object[] row = new Object[]{ s, "Design Doc", "Bob", 99L };

            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of(row));

            GroupFileListDto result = sut.listGroupFiles(1L, alice); // alice is not the uploader

            assertThat(result.getFiles().get(0).getIsOwnSubmission()).isFalse();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown group")
        void unknownGroup_throwsNotFound() {
            when(groupRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.listGroupFiles(999L, alice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group");
        }

        @Test
        @DisplayName("Summary fields are computed correctly")
        void summary_computedCorrectly() {
            Submission s1 = makeSubmission(1L, "file1.pdf", 1_048_576L);   // 1 MB
            Submission s2 = makeSubmission(2L, "file2.zip", 2_097_152L);   // 2 MB
            List<Object[]> rows = List.of(
                new Object[]{ s1, "Task A", "Alice", 10L },
                new Object[]{ s2, "Task B", "Bob",   20L }
            );

            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(rows);

            GroupFileListDto result = sut.listGroupFiles(1L, alice);

            assertThat(result.getSummary().getUniqueUploaders()).isEqualTo(2);
            assertThat(result.getSummary().getUniqueTasks()).isEqualTo(2);
            assertThat(result.getTotalSizeBytes()).isEqualTo(3_145_728L); // 3 MB
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // uploadFile
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("uploadFile")
    class UploadFileTests {

        @Test
        @DisplayName("Upload succeeds: cloud URL saved to DB")
        void uploadSuccess_savesToDb() {
            MultipartFile file = new MockMultipartFile(
                "file", "design.pdf", "application/pdf", new byte[1024]);

            when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
            when(storageService.upload(file, 1L, 5L))
                .thenReturn("https://res.cloudinary.com/test/raw/upload/submissions/group-1/task-5/design.pdf");
            when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(inv -> {
                    Submission s = inv.getArgument(0);
                    s.setId(200L);
                    return s;
                });

            UploadResultDto result = sut.uploadFile(file, 5L, alice);

            assertThat(result.getSubmissionId()).isEqualTo(200L);
            assertThat(result.getFileName()).isEqualTo("design.pdf");
            assertThat(result.getFileUrl()).contains("cloudinary.com");
            assertThat(result.getMessage()).contains("successfully");
            verify(storageService).upload(file, 1L, 5L);
            verify(submissionRepository).save(any(Submission.class));
        }

        @Test
        @DisplayName("Upload fails: storage throws — no DB record created, orphan file deleted")
        void uploadFails_storageError_orphanDeleted() {
            MultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", new byte[1024]);

            when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
            when(storageService.upload(file, 1L, 5L))
                .thenThrow(new FileStorageException("Upload quota exceeded"));
            when(storageService.delete(anyString())).thenReturn();

            assertThatThrownBy(() -> sut.uploadFile(file, 5L, alice))
                .isInstanceOf(FileStorageException.class);

            verify(storageService).upload(file, 1L, 5L);       // attempted
            verify(storageService).delete(anyString());           // orphan cleaned
            verify(submissionRepository, never()).save(any());  // no DB record
        }

        @Test
        @DisplayName("Upload fails: DB save throws — orphan cloud file deleted")
        void uploadFails_dbError_orphanDeleted() {
            MultipartFile file = new MockMultipartFile(
                "file", "ok.pdf", "application/pdf", new byte[1024]);
            String cloudUrl = "https://cloudinary.com/file.pdf";

            when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
            when(storageService.upload(file, 1L, 5L)).thenReturn(cloudUrl);
            when(submissionRepository.save(any(Submission.class)))
                .thenThrow(new RuntimeException("DB constraint violation"));
            doNothing().when(storageService).delete(cloudUrl);

            assertThatThrownBy(() -> sut.uploadFile(file, 5L, alice))
                .isInstanceOf(RuntimeException.class);

            verify(storageService).delete(cloudUrl); // rollback
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown task")
        void unknownTask_throwsNotFound() {
            MultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[1]);
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.uploadFile(file, 999L, alice))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // generateDownloadUrl
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateDownloadUrl")
    class DownloadUrlTests {

        @Test
        @DisplayName("Returns download URL when file exists in storage")
        void fileExists_returnsDownloadUrl() {
            Submission sub = makeSubmission(300L, "final.pdf", 500_000L);
            String cloudUrl = "https://res.cloudinary.com/test/raw/upload/submissions/group-1/task-5/final.pdf";

            when(submissionRepository.findById(300L)).thenReturn(Optional.of(sub));
            when(storageService.exists(cloudUrl)).thenReturn(true);
            when(storageService.generateDownloadUrl(cloudUrl, "final.pdf"))
                .thenReturn("https://res.cloudinary.com/test/raw/upload/fl_attachment/final.pdf");

            FileDownloadDto result = sut.generateDownloadUrl(300L);

            assertThat(result.getDownloadUrl()).contains("fl_attachment");
            assertThat(result.getFileName()).isEqualTo("final.pdf");
            assertThat(result.getFileSizeBytes()).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("Throws FILE_MISSING_FROM_STORAGE when cloud file is gone but DB record exists")
        void fileMissing_throwsFileMissingException() {
            Submission sub = makeSubmission(301L, "ghost.pdf", 1000L);

            when(submissionRepository.findById(301L)).thenReturn(Optional.of(sub));
            when(storageService.exists(anyString())).thenReturn(false);

            assertThatThrownBy(() -> sut.generateDownloadUrl(301L))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("FILE_MISSING_FROM_STORAGE");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown submission")
        void unknownSubmission_throwsNotFound() {
            when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.generateDownloadUrl(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private Submission makeSubmission(Long id, String filename, Long size) {
        return Submission.builder()
            .id(id)
            .fileName(filename)
            .fileUrl("https://cloudinary.com/test/" + filename)
            .fileSize(size)
            .contentType("application/pdf")
            .submittedAt(LocalDateTime.now())
            .task(task)
            .build();
    }
}
