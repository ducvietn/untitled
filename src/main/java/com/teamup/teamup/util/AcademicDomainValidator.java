package com.teamup.teamup.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates that an email address belongs to a recognised academic domain.
 *
 * <h3>Config</h3>
 * Academic domains are configured as a comma-separated list of regex patterns
 * in {@code academic-domains.whitelist} in {@code application.yml}:
 * <pre>
 * academic-domains:
 *   whitelist:
 *     - "^.+@(.+\\.)?vnu\\.edu\\.vn$"
 *     - "^.+@(.+\\.)?usth\\.edu\\.vn$"
 *     - "^.+@.+\\.edu\\.vn$"          # any .edu.vn subdomain
 * </pre>
 *
 * <h3>Decision table</h3>
 * <table>
 *   <tr><th>Email domain</th><th>Result</th></tr>
 *   <tr><td>john@vnu.edu.vn</td><td>✓ VALID (exact match)</td></tr>
 *   <tr><td>dean@hq.vnu.edu.vn</td><td>✓ VALID (subdomain match)</td></tr>
 *   <tr><td>prof@usth.edu.vn</td><td>✓ VALID (exact match)</td></tr>
 *   <tr><td>lecturer@eng.hcmiu.edu.vn</td><td>✓ VALID (.edu.vn generic)</td></tr>
 *   <tr><td>student@gmail.com</td><td>✗ INVALID (consumer domain)</td></tr>
 *   <tr><td>teacher@company.edu.vn</td><td>✗ INVALID (non-recognised edu)</td></tr>
 * </table>
 *
 * <h3>Thread-safety</h3>
 * All compiled {@code Pattern} objects are immutable and thread-safe.
 * The list is built once at construction and never modified at runtime.
 */
@Component
@Slf4j
public class AcademicDomainValidator {

    private final List<Pattern> patterns;

    /**
     * Constructor — compiles the configured regex patterns into {@code Pattern} objects.
     * Spring injects the value list from application.yml.
     *
     * @param whitelistPatterns list of regex patterns from {@code academic-domains.whitelist}
     */
    public AcademicDomainValidator(
            @Value("${academic-domains.whitelist:}") List<String> whitelistPatterns) {

        if (whitelistPatterns == null || whitelistPatterns.isEmpty()) {
            log.warn("academic-domains.whitelist is empty — NO academic emails will be auto-approved!");
            this.patterns = List.of();
        } else {
            this.patterns = whitelistPatterns.stream()
                .map(Pattern::compile)
                .toList();
            log.info("AcademicDomainValidator loaded {} pattern(s): {}", patterns.size(), whitelistPatterns);
        }
    }

    /**
     * Returns true if the given email address matches at least one configured
     * academic domain pattern.
     *
     * @param email the email to validate (case-insensitive)
     * @return true if the domain is an approved academic domain
     */
    public boolean isAcademicEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String lower = email.toLowerCase();
        boolean matched = patterns.stream().anyMatch(p -> p.matcher(lower).matches());
        log.debug("AcademicDomainValidator: {} → {}", email, matched ? "VALID" : "INVALID");
        return matched;
    }

    /**
     * Returns the matched pattern name (derived from the pattern string itself)
     * for logging and audit trail purposes.
     *
     * @param email the email to look up
     * @return the matched pattern string, or null if no match
     */
    public String matchedPattern(String email) {
        if (email == null || email.isBlank()) return null;
        String lower = email.toLowerCase();
        return patterns.stream()
            .filter(p -> p.matcher(lower).matches())
            .map(Object::toString)
            .findFirst()
            .orElse(null);
    }
}
