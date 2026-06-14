package com.teamup.teamup.service.auth;

import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.AccountStatus;
import com.teamup.teamup.enums.Role;
import com.teamup.teamup.repository.UserRepository;
import com.teamup.teamup.security.JwtService;
import com.teamup.teamup.util.AcademicDomainValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistrationService.
 *
 * Tests the instant auto-approval flow for teachers and the
 * 403 rejection for non-academic emails.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository         userRepository;

    @Mock
    private PasswordEncoder        passwordEncoder;

    @Mock
    private JwtService            jwtService;

    @Mock
    private AcademicDomainValidator domainValidator;

    @InjectMocks
    private RegistrationService sut;

    @BeforeEach
    void setUp() {
        // Default mocks
        when(passwordEncoder.encode(anyString())).thenReturn("$2b$12$hashedpassword...");
        when(jwtService.generateToken(anyLong(), anyString(), anyString()))
            .thenReturn("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIn0.fake");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Student registration
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Student registration — instant approval")
    class StudentRegistration {

        @Test
        @DisplayName("Student is registered immediately with ACTIVE status")
        void studentRegisteredActive() {
            when(userRepository.existsByEmailIgnoreCase("alice@student.edu")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            RegistrationService.RegistrationResult result =
                sut.register("Alice", "alice@student.edu", "password123", Role.ROLE_STUDENT);

            assertThat(result.autoApproved()).isFalse();
            assertThat(result.user().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(result.jwtToken()).isNotBlank();
            assertThat(result.user().getRole()).isEqualTo(Role.ROLE_STUDENT);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Duplicate email throws IllegalArgumentException")
        void duplicateEmail_throws() {
            when(userRepository.existsByEmailIgnoreCase("alice@student.edu")).thenReturn(true);

            assertThatThrownBy(() ->
                sut.register("Alice", "alice@student.edu", "pass", Role.ROLE_STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Teacher registration — academic domains
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Teacher registration — instant approval for academic emails")
    class TeacherAcademicRegistration {

        @Test
        @DisplayName("Academic email → ACTIVE status + JWT issued immediately")
        void academicEmail_activatedAndJwtIssued() {
            when(userRepository.existsByEmailIgnoreCase("prof@vnu.edu.vn")).thenReturn(false);
            when(domainValidator.isAcademicEmail("prof@vnu.edu.vn")).thenReturn(true);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(2L);
                return u;
            });

            RegistrationService.RegistrationResult result =
                sut.register("Dr. Nguyen", "prof@vnu.edu.vn", "secure123", Role.ROLE_TEACHER);

            assertThat(result.autoApproved()).isTrue();
            assertThat(result.user().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(result.jwtToken()).isNotBlank();
            assertThat(result.user().getRole()).isEqualTo(Role.ROLE_TEACHER);
            assertThat(result.message()).contains("academic domain verified");
        }

        @Test
        @DisplayName("Academic email → JWT contains ROLE_TEACHER")
        void academicEmail_jwtHasTeacherRole() {
            when(userRepository.existsByEmailIgnoreCase("dean@usth.edu.vn")).thenReturn(false);
            when(domainValidator.isAcademicEmail("dean@usth.edu.vn")).thenReturn(true);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(3L);
                return u;
            });

            RegistrationService.RegistrationResult result =
                sut.register("Dean", "dean@usth.edu.vn", "pass", Role.ROLE_TEACHER);

            assertThat(result.autoApproved()).isTrue();
            verify(jwtService).generateToken(3L, "dean@usth.edu.vn", "ROLE_TEACHER");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Teacher registration — non-academic domains
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Teacher registration — 403 for non-academic emails")
    class TeacherNonAcademicRegistration {

        @Test
        @DisplayName("Non-academic email → InvalidAcademicEmailException (→ HTTP 403)")
        void nonAcademicEmail_throwsForbidden() {
            when(userRepository.existsByEmailIgnoreCase("teacher@gmail.com")).thenReturn(false);
            when(domainValidator.isAcademicEmail("teacher@gmail.com")).thenReturn(false);

            assertThatThrownBy(() ->
                sut.register("Teacher", "teacher@gmail.com", "pass", Role.ROLE_TEACHER))
                .isInstanceOf(InvalidAcademicEmailException.class)
                .hasMessageContaining("Invalid academic email domain")
                .hasMessageContaining("*.edu.vn");
        }

        @Test
        @DisplayName("Non-academic email → NO user record created")
        void nonAcademicEmail_noUserCreated() {
            when(userRepository.existsByEmailIgnoreCase("user@yahoo.com")).thenReturn(false);
            when(domainValidator.isAcademicEmail("user@yahoo.com")).thenReturn(false);

            try {
                sut.register("User", "user@yahoo.com", "pass", Role.ROLE_TEACHER);
            } catch (InvalidAcademicEmailException ignored) {}

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Non-academic email → NO JWT issued")
        void nonAcademicEmail_noJwt() {
            when(userRepository.existsByEmailIgnoreCase("prof@company.com")).thenReturn(false);
            when(domainValidator.isAcademicEmail("prof@company.com")).thenReturn(false);

            try {
                sut.register("Prof", "prof@company.com", "pass", Role.ROLE_TEACHER);
            } catch (InvalidAcademicEmailException ignored) {}

            verify(jwtService, never()).generateToken(anyLong(), anyString(), anyString());
        }
    }
}
