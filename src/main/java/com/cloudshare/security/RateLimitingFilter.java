package com.cloudshare.security;

import com.cloudshare.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final JwtTokenProvider tokenProvider;
    private final ClientIpResolver clientIpResolver;

    @Value("${security.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled = true;

    @Value("${security.rate-limiting.auth-limit:5}")
    private int authLimit;

    @Value("${security.rate-limiting.upload-limit:10}")
    private int uploadLimit;

    @Value("${security.rate-limiting.link-limit:30}")
    private int linkLimit;

    @Value("${security.rate-limiting.general-limit:100}")
    private int generalLimit;

    @Value("${security.rate-limiting.mfa-limit:5}")
    private int mfaLimit;

    @Value("${security.rate-limiting.link-global-limit:100}")
    private int linkGlobalLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = clientIpResolver.resolveIp(request);

        boolean allowed = true;

        if (path.startsWith("/api/v1/")) {
            if ("POST".equalsIgnoreCase(method) &&
                    (path.equals("/api/v1/auth/login") ||
                            path.equals("/api/v1/auth/register") ||
                            path.equals("/api/v1/auth/refresh"))) {

                // Auth rate limiting
                String key = "limit:" + ip + ":" + path;
                allowed = rateLimiterService.isAllowed(key, 60, authLimit);

            } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/files/upload")) {

                // File upload rate limiting
                String userId = getUserIdFromAuthorizationHeader(request);
                String identifier = (userId != null) ? userId : ip;
                String key = "limit:" + identifier + ":" + path;
                allowed = rateLimiterService.isAllowed(key, 60, uploadLimit);

            } else if ("POST".equalsIgnoreCase(method) &&
                    (path.equals("/api/v1/auth/mfa/verify") ||
                            path.equals("/api/v1/auth/mfa/step-up"))) {

                // MFA rate limiting
                String userId = getUserIdFromAuthorizationHeader(request);
                String identifier = (userId != null) ? userId : ip;
                String key = "limit:" + identifier + ":" + path;
                allowed = rateLimiterService.isAllowed(key, 60, mfaLimit);

            } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/shares/link/")) {

                // Public link access rate limiting
                String remaining = path.substring(20); // Length of "/api/v1/shares/link/"
                int slashIdx = remaining.indexOf('/');
                String shareCode = (slashIdx != -1) ? remaining.substring(0, slashIdx) : remaining;

                String linkKey = "limit:link:" + shareCode + ":" + ip;
                String globalKey = "limit:linkglobal:" + ip;

                boolean linkAllowed = rateLimiterService.isAllowed(linkKey, 60, linkLimit);
                boolean globalAllowed = rateLimiterService.isAllowed(globalKey, 60, linkGlobalLimit);
                allowed = linkAllowed && globalAllowed;

            } else {

                // General REST API rate limiting
                String userId = getUserIdFromAuthorizationHeader(request);
                String identifier = (userId != null) ? userId : ip;
                String key = "limit:general:" + identifier;
                allowed = rateLimiterService.isAllowed(key, 60, generalLimit);
            }
        }

        if (!allowed) {
            log.warn("Rate limit exceeded for path={} method={} IP={}", path, method, ip);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please try again later.\"},\"timestamp\":\""
                            + Instant.now() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getUserIdFromAuthorizationHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            try {
                ResolvedJwt resolved = (ResolvedJwt) request.getAttribute(ResolvedJwt.REQUEST_ATTRIBUTE);
                if (resolved == null || !jwt.equals(resolved.token())) {
                    resolved = tokenProvider.resolveToken(jwt);
                    if (resolved == null) {
                        // Fallback for mock/legacy provider stubs
                        boolean valid = tokenProvider.validateToken(jwt);
                        String userId = valid ? tokenProvider.getUserIdFromToken(jwt) : null;
                        resolved = new ResolvedJwt(jwt, valid, userId, null, null);
                    }
                    request.setAttribute(ResolvedJwt.REQUEST_ATTRIBUTE, resolved);
                }
                if (resolved.valid()) {
                    return resolved.userId();
                }
            } catch (Exception e) {
                log.debug("Silent failure parsing Bearer token for rate limiting user identification: {}",
                        e.getMessage());
            }
        }
        return null;
    }
}
