package com.cloudshare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class StepUpAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

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

            if (stepUpToken == null || !tokenProvider.validateStepUpToken(stepUpToken, principal.getId().toString())) {
                log.warn("Step-up validation failed for user {} attempting to access {}", principal.getUsername(), path);
                
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid bearer/step-up token.\"},\"timestamp\":\"" + Instant.now() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
