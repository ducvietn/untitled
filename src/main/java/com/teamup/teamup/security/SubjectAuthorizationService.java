package com.teamup.teamup.security;

import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.SubmissionRepository;
import com.teamup.teamup.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Enforces Subject-Scoped Authorization for teacher operations.
 *
 * <h3>Access model</h3>
 * <pre>
 * ROLE_ADMIN    → unrestricted: can access ALL groups, ALL subjects
 * ROLE_TEACHER → scoped: can only access groups where
 *                  group.subjectCode == teacher.user.subjectCode
 * </pre>
 *
 * <h3>SpEL integration</h3>
 * This bean is exposed as {@code @subjectAuth} so it can be used in annotations:
 * <pre>
 * {@code @PreAuthorize("@subjectAuth.canAccessGroup(#groupId, authentication)")}
 * {@code @PreAuthorize("@subjectAuth.canAccessTask(#taskId, authentication)")}
 * {@code @PreAuthorize("@subjectAuth.canAccessSubmission(#id, authentication)")}
 * </pre>
 *
 * <h3>Thread-safety</h3>
 * All methods are stateless and read-only (queries only, no mutations).
 * The underlying repository calls are thread-safe.
 */
@Service("subjectAuth")
@RequiredArgsConstructor
@Slf4j
public class SubjectAuthorizationService {

    private final GroupRepository     groupRepository;
    private final TaskRepository    taskRepository;
    private final SubmissionRepository submissionRepository;

    // ══════════════════════════════════════════════════════════════════════════════
    // Group-level scope check
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * SpEL: {@code @subjectAuth.canAccessGroup(#groupId, authentication)}
     */
    public boolean canAccessGroup(Long groupId, Authentication authentication) {
        if (!isAuthenticated(authentication)) return false;
        CustomUserDetails principal = extractPrincipal(authentication);
        if (principal == null) return false;

        if (hasRole(authentication, "ROLE_ADMIN")) return true;
        if (hasRole(authentication, "ROLE_TEACHER")) {
            return canTeacherAccessGroup(principal, groupId);
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Task-level scope check (delegates to group via indexed lookup)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * SpEL: {@code @subjectAuth.canAccessTask(#taskId, authentication)}
     *
     * Looks up the task's groupId via the indexed FK, then checks the group scope.
     */
    public boolean canAccessTask(Long taskId, Authentication authentication) {
        if (!isAuthenticated(authentication)) return false;
        if (hasRole(authentication, "ROLE_ADMIN")) return true;
        if (!hasRole(authentication, "ROLE_TEACHER")) return false;

        return taskRepository.findGroupIdById(taskId)
            .map(groupId -> canTeacherAccessGroup(extractPrincipal(authentication), groupId))
            .orElse(false);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Submission-level scope check (delegates to group via indexed lookup)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * SpEL: {@code @subjectAuth.canAccessSubmission(#submissionId, authentication)}
     *
     * Looks up the submission's task's groupId, then checks the group scope.
     */
    public boolean canAccessSubmission(Long submissionId, Authentication authentication) {
        if (!isAuthenticated(authentication)) return false;
        if (hasRole(authentication, "ROLE_ADMIN")) return true;
        if (!hasRole(authentication, "ROLE_TEACHER")) return false;

        return submissionRepository.findTaskGroupIdBySubmissionId(submissionId)
            .map(groupId -> canTeacherAccessGroup(extractPrincipal(authentication), groupId))
            .orElse(false);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Subject-level scope check
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * SpEL: {@code @subjectAuth.canAccessSubject(#subjectCode, authentication)}
     */
    public boolean canAccessSubject(String subjectCode, Authentication authentication) {
        if (!isAuthenticated(authentication)) return false;
        CustomUserDetails principal = extractPrincipal(authentication);
        if (principal == null) return false;

        if (hasRole(authentication, "ROLE_ADMIN")) return true;
        if (hasRole(authentication, "ROLE_TEACHER")) {
            return matchesSubjectCode(principal, subjectCode);
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Enforcement (throws)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Throws {@link SubjectAccessDeniedException} if the teacher cannot access
     * the given group. Call this before any write operation.
     */
    public void requireGroupAccess(Long groupId, Authentication authentication) {
        if (!canAccessGroup(groupId, authentication)) {
            CustomUserDetails principal = extractPrincipal(authentication);
            String teacherSubject = principal != null ? principal.getSubjectCode() : "unknown";
            throw new SubjectAccessDeniedException(
                String.format(
                    "Access denied: You are not authorized to manage this subject domain. " +
                    "Your registered subject is '%s' but this group belongs to a different subject.",
                    teacherSubject),
                teacherSubject);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private boolean canTeacherAccessGroup(CustomUserDetails principal, Long groupId) {
        if (principal.getSubjectCode() == null) {
            log.warn("Teacher {} has no subject_code", principal.getUserId());
            return false;
        }
        return groupRepository.findSubjectCodeById(groupId)
            .map(groupSubject -> {
                boolean allowed = principal.getSubjectCode().equalsIgnoreCase(groupSubject);
                log.debug("Scope check: teacher.subject={}, group.subject={}, allowed={}",
                    principal.getSubjectCode(), groupSubject, allowed);
                return allowed;
            })
            .orElse(false);
    }

    private boolean matchesSubjectCode(CustomUserDetails principal, String subjectCode) {
        return principal.getSubjectCode() != null
            && principal.getSubjectCode().equalsIgnoreCase(subjectCode);
    }

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal());
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(r -> r.equals(role));
    }

    private CustomUserDetails extractPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        return (p instanceof CustomUserDetails cud) ? cud : null;
    }
}
