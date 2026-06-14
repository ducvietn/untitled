package com.teamup.teamup.controller;

import com.teamup.teamup.entity.Class;
import com.teamup.teamup.entity.Submission;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.enums.TaskStatus;
import com.teamup.teamup.exception.ApiResponse;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.repository.ClassRepository;
import com.teamup.teamup.security.CustomUserDetails;
import com.teamup.teamup.security.SubjectAuthorizationService;
import com.teamup.teamup.service.dto.BottleneckReportDto;
import com.teamup.teamup.service.dto.GodEyeDashboardDto;
import com.teamup.teamup.service.dto.GroupProgressDto;
import com.teamup.teamup.service.dto.MemberContributionDto;
import com.teamup.teamup.service.impl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Teacher portal — subject-scoped oversight and master override APIs.
 *
 * <h3>Security model</h3>
 * Every endpoint requires {@code hasAnyRole('TEACHER','ADMIN')}.
 * Additionally, every operation carries a scope check via {@code @subjectAuth}:
 * <ul>
 *   <li>Group operations: {@code @subjectAuth.canAccessGroup(#groupId, authentication)}</li>
 *   <li>Task operations: {@code @subjectAuth.canAccessTask(#taskId, authentication)}</li>
 *   <li>Submission operations: {@code @subjectAuth.canAccessSubmission(#id, authentication)}</li>
 *   <li>Class operations: {@code @subjectAuth.canAccessSubject(#subjectCode, authentication)}</li>
 * </ul>
 * ROLE_ADMIN bypasses all scope checks (global access).
 *
 * <h3>URL-manipulation protection</h3>
 * If a teacher manipulates a URL to access a resource outside their subject domain,
 * {@code SubjectAccessDeniedException} → HTTP 403 with:
 * "Access denied: You are not authorized to manage this subject domain."
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Scope Check</th></tr>
 *   <tr><td>GET</td><td>/teacher/overview</td><td>Teacher's own subject</td></tr>
 *   <tr><td>GET</td><td>/teacher/classes</td><td>Teacher's own subject</td></tr>
 *   <tr><td>GET</td><td>/teacher/classes/{id}/dashboard</td><td>Class's subject</td></tr>
 *   <tr><td>GET</td><td>/teacher/groups/{id}/progress</td><td>Group's subject</td></tr>
 *   <tr><td>GET</td><td>/teacher/groups/{id}/contributions</td><td>Group's subject</td></tr>
 *   <tr><td>GET</td><td>/teacher/groups/{id}/bottlenecks</td><td>Group's subject</td></tr>
 *   <tr><td>GET</td><td>/teacher/groups/{id}/files</td><td>Group's subject</td></tr>
 *   <tr><td>PATCH</td><td>/teacher/tasks/{id}/deadline</td><td>Task's group subject</td></tr>
 *   <tr><td>PUT</td><td>/teacher/tasks/{id}/reassign</td><td>Task's group subject</td></tr>
 *   <tr><td>PUT</td><td>/teacher/tasks/{id}</td><td>Task's group subject</td></tr>
 *   <tr><td>DELETE</td><td>/teacher/tasks/{id}</td><td>Task's group subject</td></tr>
 *   <tr><td>POST</td><td>/teacher/tasks/{id}/unlock</td><td>Task's group subject</td></tr>
 *   <tr><td>POST</td><td>/teacher/submissions/{id}/approve</td><td>Submission's group subject</td></tr>
 *   <tr><td>POST</td><td>/teacher/submissions/{id}/reject</td><td>Submission's group subject</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@Slf4j
public class TeacherController {

    private final GodEyeDashboardService     godEyeService;
    private final GroupProgressService       groupProgressService;
    private final ProgressCalculationService  contributionService;
    private final BottleneckDetectionService bottleneckService;
    private final MasterOverrideService      overrideService;
    private final ClassRepository            classRepository;
    private final SubjectAuthorizationService subjectAuth;

    // ══════════════════════════════════════════════════════════════════════════════
    // Overview
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/teacher/overview
     * Returns dashboard data for all classes in the teacher's own subject_code.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TeacherOverviewDto>> getOverview(
            @AuthenticationPrincipal CustomUserDetails teacher) {

        String subjectCode = teacher.getSubjectCode();
        log.info("Teacher overview: teacherId={}, subject={}", teacher.getUserId(), subjectCode);

        List<Class> classes = classRepository.findByOwnerIdAndSubjectCode(
            teacher.getUserId(), subjectCode);

        List<ClassOverviewItem> classItems = classes.stream()
            .map(c -> {
                GodEyeDashboardDto dash = godEyeService.buildDashboard(c);
                return new ClassOverviewItem(
                    c.getId(), c.getClassName(), c.getClassCode(), c.getSubjectCode(),
                    c.getSemester(),
                    dash.getSummary().getTotalGroups(),
                    dash.getSummary().getAtRiskGroups(),
                    dash.getSummary().getAverageGroupProgress(),
                    dash.getSummary().getOverdueTasks(),
                    dash.getGroups().stream()
                        .filter(g -> g.getHealthStatus() != GodEyeDashboardDto.HealthStatus.GREEN)
                        .map(g -> g.getGroupName() + " (" + g.getHealthStatus() + ")")
                        .toList()
                );
            })
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(new TeacherOverviewDto(
            teacher.getUserId(), teacher.getEmail(), subjectCode,
            classes.size(), classItems)));
    }

    /**
     * GET /api/teacher/classes
     * Returns all classes owned by the teacher (scoped to their subject).
     */
    @GetMapping("/classes")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ClassSummaryDto>>> getClasses(
            @AuthenticationPrincipal CustomUserDetails teacher) {

        List<Class> classes = classRepository.findByOwnerIdAndSubjectCode(
            teacher.getUserId(), teacher.getSubjectCode());

        List<ClassSummaryDto> summaries = classes.stream()
            .map(c -> new ClassSummaryDto(
                c.getId(), c.getClassName(), c.getClassCode(),
                c.getSubjectCode(), c.getSubjectName(), c.getSemester(),
                c.getGroups().size()))
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    /**
     * GET /api/teacher/classes/{classId}/dashboard
     * Subject-scoped: class's subject_code must match teacher's subject_code.
     */
    @GetMapping("/classes/{classId}/dashboard")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') " +
        "and @subjectAuth.canAccessSubject(@classRepository.findById(#classId).orElse(null)?.subjectCode, authentication)")
    public ResponseEntity<ApiResponse<GodEyeDashboardDto>> getClassDashboard(
            @PathVariable Long classId) {

        Class cls = classRepository.findById(classId)
            .orElseThrow(() -> new ResourceNotFoundException("Class", classId));

        return ResponseEntity.ok(ApiResponse.ok(godEyeService.buildDashboard(cls)));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Group-scoped read APIs
    // ══════════════════════════════════════════════════════════════════════════════

    @GetMapping("/groups/{groupId}/progress")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessGroup(#groupId, authentication)")
    public ResponseEntity<ApiResponse<GroupProgressDto>> getGroupProgress(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupProgressService.computeGroupProgress(groupId)));
    }

    @GetMapping("/groups/{groupId}/contributions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessGroup(#groupId, authentication)")
    public ResponseEntity<ApiResponse<Map<Long, MemberContributionDto>>> getContributions(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok(
            contributionService.calculateAllContributionsInGroup(groupId)));
    }

    @GetMapping("/groups/{groupId}/bottlenecks")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessGroup(#groupId, authentication)")
    public ResponseEntity<ApiResponse<BottleneckReportDto>> getBottlenecks(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok(bottleneckService.detectBottlenecks(groupId)));
    }

    @GetMapping("/groups/{groupId}/files")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessGroup(#groupId, authentication)")
    public ResponseEntity<ApiResponse<List<Submission>>> getAllFiles(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok(overrideService.getAllFilesInGroup(groupId)));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Master Override — Task Operations (task-scoped)
    // ══════════════════════════════════════════════════════════════════════════════

    @PatchMapping("/tasks/{taskId}/deadline")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessTask(#taskId, authentication)")
    public ResponseEntity<ApiResponse<TaskDto>> forceExtendDeadline(
            @PathVariable Long taskId,
            @RequestBody ForceExtendDeadlineRequest req,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        Task t = overrideService.forceExtendDeadline(taskId, req.newDeadline(), teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Deadline extended.", toDto(t)));
    }

    @PutMapping("/tasks/{taskId}/reassign")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessTask(#taskId, authentication)")
    public ResponseEntity<ApiResponse<TaskDto>> forceReassignTask(
            @PathVariable Long taskId,
            @RequestBody ForceReassignRequest req,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        Task t = overrideService.forceReassignTask(taskId, req.newUserId(), teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Task reassigned.", toDto(t)));
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessTask(#taskId, authentication)")
    public ResponseEntity<ApiResponse<TaskDto>> forceModifyTask(
            @PathVariable Long taskId,
            @RequestBody ForceModifyTaskRequest req,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        Task t = overrideService.forceModifyTask(taskId,
            req.taskName(), req.description(), req.deadline(), req.progress(), req.status(),
            teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Task modified.", toDto(t)));
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessTask(#taskId, authentication)")
    public ResponseEntity<ApiResponse<Void>> forceDeleteTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        overrideService.forceDeleteTask(taskId, teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Task deleted."));
    }

    @PostMapping("/tasks/{taskId}/unlock")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessTask(#taskId, authentication)")
    public ResponseEntity<ApiResponse<TaskDto>> forceUnlockTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        Task t = overrideService.forceUnlockTask(taskId, teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Task unlocked.", toDto(t)));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Master Override — Submission Operations (submission-scoped)
    // ══════════════════════════════════════════════════════════════════════════════

    @PostMapping("/submissions/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessSubmission(#id, authentication)")
    public ResponseEntity<ApiResponse<TaskDto>> forceApprove(
            @PathVariable("id") Long submissionId,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        Task t = overrideService.forceApproveSubmission(submissionId, teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Submission approved.", toDto(t)));
    }

    @PostMapping("/submissions/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN') and @subjectAuth.canAccessSubmission(#id, authentication)")
    public ResponseEntity<ApiResponse<TaskDto>> forceReject(
            @PathVariable("id") Long submissionId,
            @RequestBody(required = false) ForceRejectRequest req,
            @AuthenticationPrincipal CustomUserDetails teacher) {
        Task t = overrideService.forceRejectSubmission(
            submissionId, req != null ? req.reason() : null, teacher.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Submission rejected.", toDto(t)));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // DTOs
    // ══════════════════════════════════════════════════════════════════════════════

    public record ForceExtendDeadlineRequest(LocalDateTime newDeadline) {}
    public record ForceReassignRequest(Long newUserId) {}
    public record ForceModifyTaskRequest(
        String taskName, String description,
        LocalDateTime deadline, Integer progress, TaskStatus status) {}
    public record ForceRejectRequest(String reason) {}

    public record TeacherOverviewDto(
        Long teacherId, String email, String subjectCode,
        int totalClasses, List<ClassOverviewItem> classes) {}

    public record ClassOverviewItem(
        Long classId, String className, String classCode,
        String subjectCode, String semester,
        int totalGroups, int atRiskGroups,
        Double avgProgress, int overdueTasks,
        List<String> atRiskGroupNames) {}

    public record ClassSummaryDto(
        Long classId, String className, String classCode,
        String subjectCode, String subjectName, String semester,
        int groupCount) {}

    public record TaskDto(
        Long taskId, String taskName, Integer progress, TaskStatus status,
        String deadline,
        Long assigneeId, String assigneeName,
        Long groupId, String subjectCode) {}

    private TaskDto toDto(Task t) {
        return new TaskDto(
            t.getId(), t.getTaskName(), t.getProgress(), t.getStatus(),
            t.getDeadline().toString(),
            t.getAssignedTo().getId(), t.getAssignedTo().getName(),
            t.getGroup().getId(), t.getGroup().getSubjectCode());
    }
}
