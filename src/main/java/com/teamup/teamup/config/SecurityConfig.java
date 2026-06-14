package com.teamup.teamup.config;

import com.teamup.teamup.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main Spring Security HTTP filter chain configuration.
 *
 * <h3>Session strategy: stateless JWT</h3>
 * {@code SessionCreationPolicy.STATELESS} — no servlet sessions are created.
 * Every request must carry a valid JWT in the {@code Authorization: Bearer <token>} header.
 *
 * <h3>Authentication flow</h3>
 * <pre>
 * Request → JwtAuthenticationFilter (extracts JWT, validates, populates SecurityContext)
 *                     ↓
 *            AuthorizationManager (checks @PreAuthorize on the controller method)
 *                     ↓
 *            Controller method executes
 * </pre>
 *
 * <h3>Role hierarchy</h3>
 * <pre>
 * ROLE_ADMIN  →  ROLE_TEACHER  →  ROLE_STUDENT
 * </pre>
 * ROLE_ADMIN inherits all ROLE_TEACHER permissions.
 * ROLE_TEACHER inherits all ROLE_STUDENT permissions.
 *
 * This means:
 * - A controller method with {@code hasRole('TEACHER')} is also accessible by ROLE_ADMIN.
 * - A controller method with {@code hasRole('STUDENT')} is accessible by both
 *   ROLE_TEACHER and ROLE_ADMIN.
 *
 * <h3>Omnipotent Teacher Access</h3>
 * Because the filter chain is stateless and every request is individually
 * authorised at the method level (via {@code @PreAuthorize}), ROLE_TEACHER
 * automatically has unrestricted access to ALL endpoints that require
 * {@code hasRole('TEACHER')}. The check is purely role-based — there is
 * NO group-membership check in the security layer.
 *
 * Group membership checks (student can only access their own group's tasks)
 * are enforced at the SERVICE layer, not the security layer.
 * The teacher bypasses service-layer ownership checks via
 * {@code @PreAuthorize("hasRole('TEACHER')")} on the controller.
 *
 * @see MethodSecurityConfig for the {@code @PreAuthorize} configuration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity        // activates @PreAuthorize / @PostAuthorize annotations
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService     userDetailsService;

    // ══════════════════════════════════════════════════════════════════════════════
    // Password encoder
    // ══════════════════════════════════════════════════════════════════════════════

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with cost factor 12 — industry standard for password hashing
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Never reveal whether the failure was username-not-found vs bad-password
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CORS — allow frontend to send JWT from any origin
    // ══════════════════════════════════════════════════════════════════════════════

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization")); // allow frontend to read JWT
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Security filter chain
    // ══════════════════════════════════════════════════════════════════════════════

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── Disable CSRF (not needed for stateless JWT) ────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS (configured above) ───────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Session management: fully stateless ───────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Route-level authorisation ───────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // ── Public endpoints ────────────────────────────────────────────
                // Registration — open (domain validation happens in the service)
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                // Login
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                // Cloudinary signed-URL webhook (if used)
                .requestMatchers("/webhooks/cloudinary/**").permitAll()
                // Actuator health (no secrets exposed)
                .requestMatchers("/actuator/health").permitAll()

                // ── Student endpoints ───────────────────────────────────────────
                // Task operations: any authenticated user can manage their own tasks
                // (ownership check is in the service layer)
                .requestMatchers(HttpMethod.POST,   "/tasks/**").authenticated()
                .requestMatchers(HttpMethod.PUT,    "/tasks/**").authenticated()
                .requestMatchers(HttpMethod.PATCH,  "/tasks/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/tasks/**").authenticated()
                .requestMatchers(HttpMethod.GET,    "/tasks/**").authenticated()

                // ── Teacher endpoints ────────────────────────────────────────────
                // Teacher-only endpoints: ROLE_TEACHER (or ROLE_ADMIN) required.
                // NO group-membership check at this layer — teacher is global.
                .requestMatchers("/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                .requestMatchers("/classes/**").hasAnyRole("TEACHER", "ADMIN")

                // ── Admin endpoints ──────────────────────────────────────────────
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // ── File endpoints ───────────────────────────────────────────────
                // Authenticated users (service layer handles group membership)
                .requestMatchers("/files/**").authenticated()
                .requestMatchers(HttpMethod.GET,  "/groups/*/files").authenticated()

                // ── All other requests require authentication ────────────────────
                .anyRequest().authenticated()
            )

            // ── Exception handling for unauthenticated requests ─────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // ── JWT filter inserted before UsernamePasswordAuthenticationFilter ───
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
