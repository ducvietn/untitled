package com.teamup.teamup.security;

import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.SubmissionRepository;
import com.teamup.teamup.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubjectAuthorizationService.
 *
 * Tests the three-layer access model:
 *   ROLE_ADMIN  → always true (global access)
 *   ROLE_TEACHER → scoped to matching subject_code
 *   Others     → false
 */
@ExtendWith(MockitoExtension.class)
class SubjectAuthorizationServiceTest {

    @Mock private GroupRepository     groupRepository;
    @Mock private TaskRepository    taskRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private Authentication      authentication;

    @InjectMocks
    private SubjectAuthorizationService sut;

    // ══════════════════════════════════════════════════════════════════════════════
    // ADMIN — global access
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ROLE_ADMIN — global omnipotent access")
    class AdminGlobalAccess {

        private CustomUserDetails adminPrincipal;

        @BeforeEach
        void setUp() {
            adminPrincipal = new CustomUserDetails(
                99L, "admin@teamup.com", "hash", null, true, List.of("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("Admin can access any group")
        void adminAccessesGroup_alwaysTrue() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(adminPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

            assertThat(sut.canAccessGroup(1L, authentication)).isTrue();
            assertThat(sut.canAccessGroup(9999L, authentication)).isTrue();
            // Never even queries the DB
            verify(groupRepository, never()).findSubjectCodeById(any());
        }

        @Test
        @DisplayName("Admin can access any subject")
        void adminAccessesSubject_alwaysTrue() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(adminPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

            assertThat(sut.canAccessSubject("INT2204", authentication)).isTrue();
            assertThat(sut.canAccessSubject("MATH101", authentication)).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // ROLE_TEACHER — subject-scoped access
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ROLE_TEACHER — subject-scoped access")
    class TeacherScopedAccess {

        private CustomUserDetails teacherPrincipal;

        @BeforeEach
        void setUp() {
            teacherPrincipal = new CustomUserDetails(
                5L, "prof@vnu.edu.vn", "hash", "INT2204", true, List.of("ROLE_TEACHER"));
        }

        @Test
        @DisplayName("Teacher can access group with matching subject_code")
        void matchingSubject_granted() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
            when(groupRepository.findSubjectCodeById(10L)).thenReturn(Optional.of("INT2204"));

            assertThat(sut.canAccessGroup(10L, authentication)).isTrue();
        }

        @Test
        @DisplayName("Teacher CANNOT access group with different subject_code")
        void differentSubject_denied() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
            when(groupRepository.findSubjectCodeById(20L)).thenReturn(Optional.of("MATH101"));

            assertThat(sut.canAccessGroup(20L, authentication)).isFalse();
        }

        @Test
        @DisplayName("Teacher with no subject_code is denied access to all groups")
        void noSubjectCode_denied() {
            CustomUserDetails teacherNoSubject = new CustomUserDetails(
                6L, "prof@vnu.edu.vn", "hash", null, true, List.of("ROLE_TEACHER"));

            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherNoSubject);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));

            assertThat(sut.canAccessGroup(10L, authentication)).isFalse();
        }

        @Test
        @DisplayName("Teacher can access subject with matching code (case-insensitive)")
        void matchingSubjectCode_allowed() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));

            assertThat(sut.canAccessSubject("INT2204", authentication)).isTrue();
            assertThat(sut.canAccessSubject("int2204", authentication)).isTrue();
            assertThat(sut.canAccessSubject("Int2204", authentication)).isTrue();
        }

        @Test
        @DisplayName("Teacher CANNOT access different subject domain")
        void differentSubject_deniedForSubject() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));

            assertThat(sut.canAccessSubject("MATH101", authentication)).isFalse();
            assertThat(sut.canAccessSubject("PHY1001", authentication)).isFalse();
        }

        @Test
        @DisplayName("Teacher cannot access non-existent group")
        void groupNotFound_denied() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
            when(groupRepository.findSubjectCodeById(999L)).thenReturn(Optional.empty());

            assertThat(sut.canAccessGroup(999L, authentication)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // requireGroupAccess — throws
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("requireGroupAccess — throws SubjectAccessDeniedException")
    class RequireGroupAccessTests {

        private CustomUserDetails teacherPrincipal;

        @BeforeEach
        void setUp() {
            teacherPrincipal = new CustomUserDetails(
                5L, "prof@vnu.edu.vn", "hash", "INT2204", true, List.of("ROLE_TEACHER"));
        }

        @Test
        @DisplayName("Throws SubjectAccessDeniedException with correct teacher subject when access denied")
        void denied_throwsWithCorrectSubject() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
            when(groupRepository.findSubjectCodeById(20L)).thenReturn(Optional.of("MATH101"));

            assertThatThrownBy(() -> sut.requireGroupAccess(20L, authentication))
                .isInstanceOf(SubjectAccessDeniedException.class)
                .hasMessageContaining("INT2204")      // teacher's subject
                .hasMessageContaining("MATH101");      // group subject
        }

        @Test
        @DisplayName("Does not throw when teacher has matching subject_code")
        void matching_noException() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(teacherPrincipal);
            when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
            when(groupRepository.findSubjectCodeById(10L)).thenReturn(Optional.of("INT2204"));

            assertThatNoException().isThrownBy(() -> sut.requireGroupAccess(10L, authentication));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Unauthenticated / anonymous
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unauthenticated / anonymous requests")
    class UnauthenticatedTests {

        @Test
        @DisplayName("Null authentication → denied")
        void nullAuth_denied() {
            assertThat(sut.canAccessGroup(1L, null)).isFalse();
            assertThat(sut.canAccessSubject("INT2204", null)).isFalse();
        }

        @Test
        @DisplayName("Anonymous user → denied")
        void anonymous_denied() {
            when(authentication.isAuthenticated()).thenReturn(false);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");

            assertThat(sut.canAccessGroup(1L, authentication)).isFalse();
            assertThat(sut.canAccessSubject("INT2204", authentication)).isFalse();
        }
    }
}
