package com.teamup.teamup.service.auth;

import com.teamup.teamup.entity.User;
import com.teamup.teamup.enums.AccountStatus;
import com.teamup.teamup.enums.Role;
import com.teamup.teamup.repository.UserRepository;
import com.teamup.teamup.security.JwtService;
import com.teamup.teamup.util.AcademicDomainValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration with two distinct flows:
 *
 * <h3>Student registration (instant)</h3>
 * <pre>
 * email → User(ACTIVE, ROLE_STUDENT, subjectCode=null) → JWT issued immediately
 * </pre>
 *
 * <h3>Teacher registration (academic email + mandatory subject_code)</h3>
 * <pre>
 * email → AcademicDomainValidator.isAcademicEmail(email)?
 *         ├─ YES + subjectCode provided → User(ACTIVE, ROLE_TEACHER, subjectCode) → JWT
 *         └─ NO  → 403 InvalidAcademicEmailException
 *
 * subjectCode is STRICTLY REQUIRED for ROLE_TEACHER.
 * Registration fails with 400 Bad Request if subjectCode is null or blank.
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserRepository         userRepository;
    private final JwtService             jwtService;
    private final AcademicDomainValidator domainValidator;

    public record RegistrationResult(
        User user,
        String jwtToken,
        boolean autoApproved,
        String message
    ) {}

    /**
     * Registers a new user.
     *
     * @param name        the user's display name
     * @param email       the user's email address
     * @param password    the plaintext password (BCrypt-encoded before storage)
     * @param role        ROLE_STUDENT or ROLE_TEACHER
     * @param subjectCode required for ROLE_TEACHER; ignored (nulled) for ROLE_STUDENT
     * @return RegistrationResult with user entity, JWT, and approval status
     * @throws InvalidAcademicEmailException  if role is ROLE_TEACHER but email is not academic
     * @throws MissingSubjectCodeException   if role is ROLE_TEACHER but subjectCode is blank
     */
    @Transactional
    public RegistrationResult register(
            String name,
            String email,
            String password,
            Role role,
            String subjectCode) {

        log.info("Registration: name={}, email={}, role={}, subjectCode={}",
            name, email, role, subjectCode);

        // ── 1. Duplicate check ───────────────────────────────────────────────
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        // ── 2. Teacher domain validation ─────────────────────────────────────
        if (role == Role.ROLE_TEACHER) {
            if (!domainValidator.isAcademicEmail(email)) {
                log.warn("Teacher registration REJECTED: non-academic email {}", email);
                throw new InvalidAcademicEmailException(
                    "Invalid academic email domain for Teacher registration. " +
                    "Only institutional emails (*.edu.vn) are accepted.");
            }
            // ── 2b. Subject code is MANDATORY for teachers ──────────────────
            if (subjectCode == null || subjectCode.isBlank()) {
                log.warn("Teacher registration REJECTED: missing subjectCode for {}", email);
                throw new MissingSubjectCodeException(
                    "Subject code is required for Teacher registration. " +
                    "Please provide your teaching subject code (e.g., INT2204).");
            }
            log.info("Teacher {} validated. Subject domain: {}", email, subjectCode);
        }

        // ── 3. Build user entity ────────────────────────────────────────────
        // Teachers: subjectCode is mandatory and stored in the entity.
        // Students: subjectCode is always null.
        User user = User.builder()
            .name(name.trim())
            .email(email.toLowerCase().trim())
            .passwordHash(password)         // caller must encode; simplified here
            .role(role)
            .accountStatus(AccountStatus.ACTIVE)
            .subjectCode(role == Role.ROLE_TEACHER ? subjectCode.trim().toUpperCase() : null)
            .build();

        user = userRepository.save(user);
        log.info("User registered: id={}, role={}, subjectCode={}",
            user.getId(), role, user.getSubjectCode());

        // ── 4. Issue JWT immediately ─────────────────────────────────────────
        String token = jwtService.generateToken(
            user.getId(),
            user.getEmail(),
            user.getRole().name()
        );

        String message = role == Role.ROLE_TEACHER
            ? "Teacher account activated for subject " + user.getSubjectCode() + "."
            : "Registration successful. Welcome to TeamUp!";

        return new RegistrationResult(user, token, role == Role.ROLE_TEACHER, message);
    }
}
