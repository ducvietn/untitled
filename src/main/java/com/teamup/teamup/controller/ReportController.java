package com.teamup.teamup.controller;

import com.teamup.teamup.dto.GroupReportDto;
import com.teamup.teamup.exception.ApiResponse;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.security.SubjectAuthorizationService;
import com.teamup.teamup.service.impl.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for teacher-only report generation.
 *
 * <h3>Security</h3>
 * - {@code hasAnyRole('TEACHER','ADMIN')} — ensures the caller is a teacher or admin
 * - {@code @subjectAuth.canAccessGroup(#groupId, authentication)} — enforces
 *   subject scoping: teacher can only export groups in their registered subject_code
 *
 * If either check fails, the request is rejected with HTTP 403.
 *
 * <h3>Response</h3>
 * Returns a binary Excel (.xlsx) file with:
 * - Content-Disposition: attachment; filename="Group_[ID]_Report.xlsx"
 * - Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
 *
 * <h3>Data sources</h3>
 * The report aggregates:
 * 1. Weighted contribution % per member (ProgressCalculationService)
 * 2. Submission log with ON_TIME / LATE flags (SubmissionRepository JOIN)
 * 3. Average peer attitude score per member (PeerReviewRepository)
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportGenerationService reportService;
    private final GroupRepository         groupRepository;
    private final SubjectAuthorizationService subjectAuth;

    // ══════════════════════════════════════════════════════════════════════════════
    // Excel export
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/reports/groups/{groupId}/export
     *
     * Generates and downloads a comprehensive group report as Excel (.xlsx).
     *
     * @param groupId   the group to generate the report for
     * @param auth      the authentication token (for subject scope check)
     * @return ResponseEntity<byte[]> with the Excel file
     */
    @GetMapping("/groups/{groupId}/export")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and " +
        "@subjectAuth.canAccessGroup(#groupId, authentication)")
    public ResponseEntity<byte[]> exportGroupReport(
            @PathVariable Long groupId,
            Authentication auth) {

        log.info("Report export: groupId={}, teacher={}",
            groupId,
            auth.getName());

        byte[] xlsxBytes = reportService.generateExcelReport(groupId);

        String filename = "Group_" + groupId + "_Report.xlsx";

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(xlsxBytes.length))
            .body(xlsxBytes);
    }

    /**
     * GET /api/reports/groups/{groupId}/preview
     *
     * Returns the raw GroupReportDto (JSON) for the frontend to render as a
     * preview without downloading the file.
     */
    @GetMapping("/groups/{groupId}/preview")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and " +
        "@subjectAuth.canAccessGroup(#groupId, authentication)")
    public ResponseEntity<ApiResponse<GroupReportDto>> previewReport(
            @PathVariable Long groupId) {

        GroupReportDto report = reportService.buildReport(groupId);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }
}
