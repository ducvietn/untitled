package com.teamup.teamup.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Service for issuing and validating JWT tokens.
 *
 * <h3>Token structure</h3>
 * <pre>
 * Header:  { "alg": "HS512", "typ": "JWT" }
 * Payload: {
 *   "sub":    "<userId>",           — principal identifier
 *   "email":  "<email>",            — for display
 *   "roles":  ["ROLE_TEACHER"],    — Spring Security authorities
 *   "iat":    1700000000,          — issued at (epoch seconds)
 *   "exp":    1700086400           — expiry (24 h default)
 * }
 * </pre>
 *
 * <h3>Security guarantees</h3>
 * <ul>
 *   <li>Algorithm: HS512 — symmetric HMAC. Key must be ≥ 512 bits (64 bytes).</li>
 *   <li>Tokens are NOT blacklisted on logout (stateless). Set short expiry
 *       (24 h) and use a sliding-window refresh token strategy in production.</li>
 *   <li>The JWT secret MUST be provided via the {@code JWT_SECRET} environment
 *       variable in production. The application will refuse to start with a weak key.</li>
 * </ul>
 */
@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 48) { // 384 bits minimum for HS384; we enforce 64 for HS512
            throw new IllegalStateException(
                "JWT_SECRET must be at least 48 characters (256 bits) for HS384 / HS512. " +
                "Current length: " + keyBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtService initialised. Token expiry: {} ms ({} hours)",
            expirationMs, expirationMs / 3_600_000.0);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Issue token
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Generates a JWT for an authenticated user.
     *
     * @param authentication the Spring Security authentication object
     * @return a signed JWT string
     */
    public String generateToken(Authentication authentication) {
        return generateToken(
            extractUserId(authentication),
            extractEmail(authentication),
            extractSubjectCode(authentication),
            extractRoles(authentication)
        );
    }

    /**
     * Generates a JWT for a user identified by their ID and email.
     *
     * @param userId       the user's primary key
     * @param email        the user's email
     * @param subjectCode  the teacher's subject code (null for students)
     * @param roles        the user's granted authorities (e.g. ["ROLE_TEACHER"])
     * @return a signed JWT string
     */
    public String generateToken(Long userId, String email, String subjectCode, String... roles) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);

        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("roles", java.util.Arrays.asList(roles))
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp));

        if (subjectCode != null) {
            builder.claim("subjectCode", subjectCode);
        }

        return builder.signWith(secretKey, Jwts.SIG.HS512).compact();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Validate token
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Validates a JWT: checks signature, expiry, and structure.
     *
     * @param token the raw JWT string
     * @return true if valid; false if expired, malformed, or tampered
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
        } catch (SignatureException ex) {
            log.warn("JWT signature invalid: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("JWT malformed: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Extracts the user ID (subject) from a token.
     */
    public Long extractUserId(String token) {
        return Long.valueOf(extractClaims(token).getSubject());
    }

    /**
     * Extracts the email claim from a token.
     */
    public String extractEmail(String token) {
        return (String) extractClaims(token).get("email", String.class);
    }

    /**
     * Extracts the roles claim from a token.
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> extractRoles(String token) {
        return (java.util.List<String>) extractClaims(token).get("roles", java.util.List.class);
    }

    /**
     * Extracts the subject_code claim from a token.
     */
    public String extractSubjectCode(String token) {
        return (String) extractClaims(token).get("subjectCode", String.class);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal
    // ══════════════════════════════════════════════════════════════════════════════

    private Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private Long extractUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            return cud.getUserId();
        }
        throw new IllegalStateException(
            "Cannot extract userId from principal type: " + principal.getClass());
    }

    private String extractEmail(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            return cud.getEmail();
        }
        return null;
    }

    private String extractSubjectCode(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            return cud.getSubjectCode();
        }
        return null;
    }

    private String[] extractRoles(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toArray(String[]::new);
    }
}
