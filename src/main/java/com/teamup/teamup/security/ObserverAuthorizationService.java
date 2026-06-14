package com.teamup.teamup.security;

import com.teamup.teamup.repository.GroupObserverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Enforces Observer-scoped read access for users with {@code ROLE_OBSERVER}.
 *
 * <h3>Access model</h3>
 * <pre>
 * ROLE_ADMIN              → unrestricted: can read ALL groups
 * ROLE_TEACHER           → subject-scoped: can read groups where
 *                            group.subjectCode == teacher.user.subjectCode
 * ROLE_OBSERVER          → group-scoped: can ONLY read groups listed in
 *                            the group_observers mapping table (active = true)
 * ROLE_STUDENT           → group-membership: can only read groups they belong to
 * </pre>
 *
 * <h3>SpEL integration</h3>
 * <pre>
 * {@code @PreAuthorize("@observerAuth.canReadGroup(#groupId, authentication)")}
 * </pre>
 */
@Service("observerAuth")
@RequiredArgsConstructor
@Slf4j
public class ObserverAuthorizationService {

    private final GroupObserverRepository groupObserverRepository;

    /**
     * SpEL: {@code @observerAuth.canReadGroup(#groupId, authentication)}
     *
     * Returns {@code true} if the authenticated user is permitted to read
     * data from the given group.
     *
     * <h3>Decision table</h3>
     * <table>
     *   <tr><th>Role</th><th>Rule</th></tr>
     *   <tr><td>ROLE_ADMIN</td><td>Always true</td></tr>
     *   <tr><td>ROLE_TEACHER</td><td>Delegate to {@link SubjectAuthorizationService}</td></tr>
     *   <tr><td>ROLE_OBSERVER</td><td>Must have active entry in {@code group_observers}</td></tr>
     *   <tr><td>ROLE_STUDENT</td><td>Must be a member of the group (delegated to service layer)</td></tr>
     * </table>
     */
    public boolean canReadGroup(Long groupId, Authentication authentication) {
        if (!isAuthenticated(authentication)) return false;

        // ADMIN is always allowed
        if (hasRole(authentication, "ROLE_ADMIN")) return true;

        // ROLE_OBSERVER: check group_observers mapping
        if (hasRole(authentication, "ROLE_OBSERVER")) {
            return groupObserverRepository
                .existsByGroupIdAndUserIdAndActiveTrue(
                    groupId, extractUserId(authentication));
        }

        // For STUDENT and TEACHER, access is determined at the service layer
        // (service checks group membership / subject scope).
        // This method only gates OBSERVER scope here.
        return false;
    }

    /**
     * Returns the set of all ACTIVE group IDs that the given user is
     * permitted to observe (ROLE_OBSERVER only).
     *
     * @param authentication the authentication token
     * @return set of accessible group IDs, or empty set if not ROLE_OBSERVER
     */
    public Set<Long> getObservableGroupIds(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_OBSERVER")) return Set.of();
        return groupObserverRepository.findActiveGroupIdsByUserId(extractUserId(authentication));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean isAuthenticated(Authentication auth) {
        return auth != null
            && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal());
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(r -> r.equals(role));
    }

    private Long extractUserId(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof CustomUserDetails cud) {
            return cud.getUserId();
        }
        return null;
    }
}
