package com.teamup.teamup.config;

import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Collection;
import java.util.List;

/**
 * Method-level security configuration.
 *
 * <h3>Role hierarchy</h3>
 * <pre>
 * ROLE_ADMIN  →  ROLE_TEACHER  →  ROLE_STUDENT
 * </pre>
 * Spring Security resolves this automatically: {@code hasRole('TEACHER')} returns true
 * for both ROLE_TEACHER and ROLE_ADMIN.
 *
 * <h3>Omnipotent Teacher access model</h3>
 *
 * The teacher is granted global, unconditional access to all teacher-scoped
 * operations via a single {@code @PreAuthorize} annotation on each controller method:
 * <pre>
 * {@code @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")}
 * </pre>
 *
 * This annotation has <strong>no group-membership check</strong>.
 * It operates purely on the user's role, not on any resource ID.
 * Therefore a ROLE_TEACHER can access ANY group's tasks, ANY submission,
 * and ANY student file — across the entire platform — without being a member
 * of that group.
 *
 * The service layer provides additional guardrails (e.g., audit logging) but
 * does NOT re-check group membership for teacher-initiated operations.
 *
 * <h3>Ownership model (students)</h3>
 * Student operations (update own progress, submit file) check ownership in the
 * service layer:
 * <pre>
 * {@code @PreAuthorize("isAuthenticated()")}
 * // in service:
 * if (!task.getAssignedTo().getId().equals(currentUser.getId())) {
 *     throw new AccessDeniedException("...");
 * }
 * </pre>
 *
 * <h3>Custom security expressions</h3>
 * {@code isGroupMember(groupId)} — checks if the current user is a member of the group.
 * Used in post-authorisation for read access.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {

    /**
     * Defines the role inheritance hierarchy.
     * ROLE_ADMIN inherits everything from ROLE_TEACHER.
     * ROLE_TEACHER inherits everything from ROLE_STUDENT.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
        hierarchy.setHierarchy(
            "ROLE_ADMIN > ROLE_TEACHER\n" +
            "ROLE_TEACHER > ROLE_STUDENT"
        );
        return hierarchy;
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            RoleHierarchy roleHierarchy) {

        DefaultMethodSecurityExpressionHandler handler =
            new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
