package com.teamup.teamup.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JAX-RS/Spring filter that intercepts every HTTP request, extracts the JWT
 * from the {@code Authorization: Bearer <token>} header, validates it,
 * and populates the Spring Security context.
 *
 * <h3>Filter order</h3>
 * This filter runs before {@code UsernamePasswordAuthenticationFilter}.
 * If a valid JWT is present, the request is treated as authenticated.
 * If absent or invalid, the request proceeds unauthenticated and will be
 * rejected by {@code AuthorizationManager} at the endpoint level.
 *
 * <h3>Error handling</h3>
 * Invalid/expired tokens are logged and the request continues without a
 * SecurityContext (the endpoint's own {@code @PreAuthorize} will reject it).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX       = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtService.validateToken(token)) {
            try {
                Long   userId = jwtService.extractUserId(token);
                String email  = jwtService.extractEmail(token);

                // Load full user details from the database to get current account status
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT authenticated user {} (id={})", email, userId);

            } catch (Exception ex) {
                log.warn("JWT authentication failed: {}", ex.getMessage());
                // Request continues unauthenticated — endpoint security will handle it
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT from the Authorization header.
     *
     * @return the token string, or null if absent / malformed
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
