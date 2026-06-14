package com.teamup.teamup.service.impl;

import com.teamup.teamup.dto.GroupReportDto;
import com.teamup.teamup.dto.GroupReportDto.SubmissionLogRow;
import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.Submission;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.repository.*;
import com.teamup.teamup.service.dto.MemberContributionDto;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportGenerationService.
 *
 * Tests the three-source data aggregation and verifies the Excel
 * file is valid and contains expected data.
 */
@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {

    @Mock private GroupRepository            groupRepository;
    @Mock private SubmissionRepository       submissionRepository;
    @Mock private PeerReviewRepository    peerReviewRepository;
    @Mock private ProgressCalculationService progressService;
    @Mock private UserRepository            userRepository;

    @InjectMocks
    private ReportGenerationService sut;

    private Group group;
    private User alice;
    private User bob;
    private Task task;
    private Submission submission;

    @BeforeEach
    void setUp() {
        group  = Group.builder().id(1L).groupName("Team Alpha")
            .subjectCode("INT2204").subjectName("Advanced Programming").build();
        group.setClassEntity(
            com.teamup.teamup.entity.Class.builder()
                .semester("2024-Fall").build());

        alice = User.builder().id(10L).name("Alice").build();
        bob   = User.builder().id(20L).name("Bob").build();
        group.setMembers(new HashSet<>(List.of(alice, bob)));

        task = Task.builder()
            .id(5L).taskName("Design Doc")
            .group(group)
            .deadline(LocalDateTime.now().minusDays(1))
            .build();

        submission = Submission.builder()
            .id(100L).fileName("report.pdf").fileSize(204800L)
            .submittedAt(LocalDateTime.now().minusHours(2))
            .task(task)
            .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // buildReport — data aggregation
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildReport — aggregates three sources")
    class BuildReport {

        @Test
        @DisplayName("Sets group metadata correctly")
        void setsGroupMetadata() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            GroupReportDto report = sut.buildReport(1L);

            assertThat(report.getGroupId()).isEqualTo(1L);
            assertThat(report.getGroupName()).isEqualTo("Team Alpha");
            assertThat(report.getSubjectCode()).isEqualTo("INT2204");
            assertThat(report.getSubjectName()).isEqualTo("Advanced Programming");
            assertThat(report.getSemester()).isEqualTo("2024-Fall");
        }

        @Test
        @DisplayName("Computes ON_TIME correctly: submitted before deadline")
        void onTime_submittedBeforeDeadline() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());

            // Submission made 2 hours before deadline
            Submission onTime = Submission.builder()
                .id(1L).fileName("early.pdf").fileSize(1000L)
                .submittedAt(task.getDeadline().minusHours(2))
                .task(task)
                .build();
            Object[] row = new Object[]{ onTime, "Design Doc", "Alice" };

            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of(row));
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            GroupReportDto report = sut.buildReport(1L);

            assertThat(report.getSubmissionLog()).hasSize(1);
            assertThat(report.getSubmissionLog().get(0).getStatus())
                .isEqualTo(SubmissionLogRow.SubmissionStatus.ON_TIME);
        }

        @Test
        @DisplayName("Computes LATE correctly: submitted after deadline")
        void late_submittedAfterDeadline() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());

            // Submission made 2 hours AFTER deadline
            Submission late = Submission.builder()
                .id(2L).fileName("late.pdf").fileSize(2000L)
                .submittedAt(task.getDeadline().plusHours(2))
                .task(task)
                .build();
            Object[] row = new Object[]{ late, "Design Doc", "Bob" };

            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of(row));
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            GroupReportDto report = sut.buildReport(1L);

            assertThat(report.getSubmissionLog()).hasSize(1);
            assertThat(report.getSubmissionLog().get(0).getStatus())
                .isEqualTo(SubmissionLogRow.SubmissionStatus.LATE);
            assertThat(report.getTotalLateSubmissions()).isEqualTo(1);
        }

        @Test
        @DisplayName("Member with no reviews → null average score in row")
        void noReviews_nullScore() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(1L, 10L)).thenReturn(null);
            when(peerReviewRepository.averageScoreForMember(1L, 20L)).thenReturn(4.0);

            GroupReportDto report = sut.buildReport(1L);

            // Alice (id=10) has no reviews
            var aliceRow = report.getMembers().stream()
                .filter(r -> r.getMemberName().equals("Alice")).findFirst().orElseThrow();
            assertThat(aliceRow.getAverageAttitudeScore()).isNull();
            assertThat(aliceRow.getAttitudeLabel()).isEqualTo("No Reviews");

            // Bob (id=20) has a 4.0 average
            var bobRow = report.getMembers().stream()
                .filter(r -> r.getMemberName().equals("Bob")).findFirst().orElseThrow();
            assertThat(bobRow.getAverageAttitudeScore()).isEqualTo(4.0);
            assertThat(bobRow.getAttitudeLabel()).isEqualTo("Good");
        }

        @Test
        @DisplayName("Rounds contribution % to 1 decimal place")
        void contributionPercent_rounded() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            // Return a contribution of 33.333...%
            MemberContributionDto contrib = MemberContributionDto.builder()
                .userId(10L)
                .contributionPercent(33.333)
                .totalTasksCompleted(5)
                .totalTasks(10)
                .build();

            when(progressService.calculateAllContributionsInGroup(1L))
                .thenReturn(Map.of(10L, contrib));

            GroupReportDto report = sut.buildReport(1L);

            var aliceRow = report.getMembers().stream()
                .filter(r -> r.getMemberName().equals("Alice")).findFirst().orElseThrow();
            assertThat(aliceRow.getContributionPercent()).isEqualTo(33.3);
        }

        @Test
        @DisplayName("Inactive member (no submissions) counted correctly")
        void inactiveMember_counted() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            GroupReportDto report = sut.buildReport(1L);

            assertThat(report.getInactiveMemberCount()).isEqualTo(2); // both members have 0 submissions
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // generateExcelReport — Excel file validity
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateExcelReport — file validity")
    class ExcelFile {

        @Test
        @DisplayName("Returns non-empty byte array")
        void returnsNonEmptyBytes() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            byte[] result = sut.generateExcelReport(1L);

            assertThat(result).isNotEmpty();
            assertThat(result.length).isGreaterThan(1024); // at least 1 KB
        }

        @Test
        @DisplayName("Byte array is a valid XLSX file (magic bytes PK)")
        void validXlsxMagicBytes() {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            byte[] result = sut.generateExcelReport(1L);

            // XLSX is a ZIP file; first two bytes of a ZIP are 'P' 'K'
            assertThat(result[0]).isEqualTo((byte) 'P');
            assertThat(result[1]).isEqualTo((byte) 'K');
        }

        @Test
        @DisplayName("XSSFWorkbook can be opened without exception")
        void workbookOpensWithoutException() throws Exception {
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(progressService.calculateAllContributionsInGroup(1L)).thenReturn(Map.of());
            when(submissionRepository.findAllFilesInGroup(1L)).thenReturn(List.of());
            when(peerReviewRepository.averageScoreForMember(anyLong(), anyLong())).thenReturn(null);

            byte[] result = sut.generateExcelReport(1L);

            assertThatCode(() -> {
                try (XSSFWorkbook wb = new XSSFWorkbook(
                        new ByteArrayInputStream(result))) {
                    assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
                    assertThat(wb.getSheetAt(0).getSheetName()).isIn("Summary", "Submission Log");
                }
            }).doesNotThrowAnyException();
        }
    }
}
