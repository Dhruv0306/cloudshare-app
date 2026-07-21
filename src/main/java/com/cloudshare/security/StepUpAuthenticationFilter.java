package com.cloudshare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Filter enforcing Multi-Factor Step-Up Authentication for administrative API
 * paths ({@code /api/v1/admin/*}).
 * <p>
 * <b>Why single-use Redis blacklisting is strictly enforced:</b> Administrative
 * actions require heightened
 * security boundaries beyond standard bearer JWTs. Step-up tokens issued via
 * MFA verification carry a 5-minute
 * TTL and must be strictly <b>single-use</b>. Upon successful step-up
 * validation, this filter immediately writes
 * {@code blacklist:token:<jti>} to the dedicated <b>Redis Security</b> instance
 * for the remaining token lifetime.
 * Any re-use attempt of the same step-up JTI (even within the 5-minute window)
 * is blocked with HTTP 401 Unauthorized.
 * The Redis blacklist is the single source of truth across all application
 * nodes; no in-memory grace periods exist.
 * </p>
 */
@Component
@Slf4j
public class StepUpAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate securityRedisTemplate;

    public StepUpAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            @Qualifier("securityRedisTemplate") StringRedisTemplate securityRedisTemplate) {
        this.tokenProvider = tokenProvider;
        this.securityRedisTemplate = securityRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // If the user is not authenticated at all, let standard Spring Security filters
            // handle it
            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal)) {
                log.debug("No active authenticated context for admin path {}. Skipping step-up check.", path);
                filterChain.doFilter(request, response);
                return;
            }

            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                log.debug("User lacks ROLE_ADMIN. Skipping step-up check to let Spring Security return 403 Forbidden.");
                filterChain.doFilter(request, response);
                return;
            }

            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            String stepUpToken = request.getHeader("X-StepUp-Token");

            String jti = null;
            try {
                if (stepUpToken != null) {
                    jti = tokenProvider.getJtiFromToken(stepUpToken);
                }
            } catch (Exception e) {
                log.debug("Failed to extract JTI from step-up token: {}", e.getMessage());
            }

            boolean isBlacklisted = false;
            if (jti != null) {
                String blacklistKey = "blacklist:token:" + jti;
                isBlacklisted = Boolean.TRUE.equals(securityRedisTemplate.hasKey(blacklistKey));
            }

            if (isBlacklisted || stepUpToken == null
                    || !tokenProvider.validateStepUpToken(stepUpToken, principal.getId().toString())) {
                log.warn("Step-up validation failed for user {} attempting to access {}", principal.getUsername(),
                        path);

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"success\":false,\"error\":{\"code\":\"STEP_UP_REQUIRED\",\"message\":\"Missing or invalid bearer/step-up token.\"},\"timestamp\":\""
                                + Instant.now() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
