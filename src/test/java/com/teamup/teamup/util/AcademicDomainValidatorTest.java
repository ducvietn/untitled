package com.teamup.teamup.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AcademicDomainValidator.
 *
 * Tests the regex-based domain validation logic in isolation.
 */
class AcademicDomainValidatorTest {

    private AcademicDomainValidator sut;

    @BeforeEach
    void setUp() {
        // Same patterns as application.yml
        sut = new AcademicDomainValidator(List.of(
            "^.+@(.+\\.)?vnu\\.edu\\.vn$",
            "^.+@(.+\\.)?usth\\.edu\\.vn$",
            "^.+@.+\\.edu\\.vn$"
        ));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Valid academic emails
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Valid academic emails")
    class ValidEmails {

        @ParameterizedTest
        @ValueSource(strings = {
            "john@vnu.edu.vn",
            "dean@hq.vnu.edu.vn",
            "lecturer@mail.hcmiu.edu.vn",
            "prof@usth.edu.vn",
            "teacher@can tho.usth.edu.vn",
            "staff@eng.hcmiu.edu.vn",
            "admin@student.hcmiu.edu.vn"
        })
        @DisplayName("Emails ending in .edu.vn are accepted")
        void validAcademicDomains(String email) {
            assertThat(sut.isAcademicEmail(email)).isTrue();
        }

        @Test
        @DisplayName("Case-insensitive matching")
        void caseInsensitive() {
            assertThat(sut.isAcademicEmail("John@VNU.EDU.VN")).isTrue();
            assertThat(sut.isAcademicEmail("Prof@USTH.EDU.VN")).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Invalid emails
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Invalid emails")
    class InvalidEmails {

        @ParameterizedTest
        @ValueSource(strings = {
            "student@gmail.com",
            "prof@company.com",
            "teacher@school.edu",
            "john@vnu.com",
            "admin@vnu.edu",
            "user@usth.com",
            "x@",
            ""
        })
        @DisplayName("Non-academic domains are rejected")
        void invalidDomains(String email) {
            assertThat(sut.isAcademicEmail(email)).isFalse();
        }

        @Test
        @DisplayName("Null email returns false")
        void nullEmail() {
            assertThat(sut.isAcademicEmail(null)).isFalse();
        }

        @Test
        @DisplayName("Blank email returns false")
        void blankEmail() {
            assertThat(sut.isAcademicEmail("   ")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Empty whitelist
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Empty whitelist — all emails are rejected")
    void emptyWhitelist() {
        AcademicDomainValidator empty = new AcademicDomainValidator(List.of());
        assertThat(empty.isAcademicEmail("john@vnu.edu.vn")).isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // matchedPattern
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("matchedPattern returns the matched regex string")
    void matchedPattern_returnsRegex() {
        String pattern = sut.matchedPattern("lecturer@eng.hcmiu.edu.vn");
        assertThat(pattern).isNotNull();
        assertThat(pattern).contains("edu\\.vn");
    }

    @Test
    @DisplayName("matchedPattern returns null for invalid email")
    void matchedPattern_invalidEmail() {
        assertThat(sut.matchedPattern("user@gmail.com")).isNull();
    }
}
