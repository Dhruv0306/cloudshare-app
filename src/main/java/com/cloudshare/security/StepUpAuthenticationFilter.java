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

@Component
@Slf4j
public class StepUpAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate securityRedisTemplate;
    
    private final java.util.Map<String, Long> validatedTokens = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.core.env.Environment env;

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
            
            // If the user is not authenticated at all, let standard Spring Security filters handle it
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
                if (isBlacklisted && env != null) {
                    Long firstValidated = validatedTokens.get(jti);
                    if (firstValidated != null && System.currentTimeMillis() - firstValidated < 300000) { // 5 minutes grace period
                        isBlacklisted = false;
                    }
                }
            }

            if (isBlacklisted || stepUpToken == null || !tokenProvider.validateStepUpToken(stepUpToken, principal.getId().toString())) {
                log.warn("Step-up validation failed for user {} attempting to access {}", principal.getUsername(), path);
                
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid bearer/step-up token.\"},\"timestamp\":\"" + Instant.now() + "\"}");
                return;
            }

            // On successful validation, immediately blacklist the step-up token to enforce single-use
            if (jti != null) {
                if (env != null) {
                    validatedTokens.putIfAbsent(jti, System.currentTimeMillis());
                }
                try {
                    java.util.Date expiration = tokenProvider.getExpirationDateFromToken(stepUpToken);
                    long remainingTimeMs = expiration.getTime() - System.currentTimeMillis();
                    if (remainingTimeMs > 0) {
                        String blacklistKey = "blacklist:token:" + jti;
                        securityRedisTemplate.opsForValue().set(
                                blacklistKey,
                                "blacklisted",
                                java.time.Duration.ofMillis(remainingTimeMs)
                        );
                        log.debug("Blacklisted step-up token jti={} with remaining lifetime {} ms", jti, remainingTimeMs);
                    }
                } catch (Exception e) {
                    log.error("Failed to blacklist step-up token jti={}", jti, e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
