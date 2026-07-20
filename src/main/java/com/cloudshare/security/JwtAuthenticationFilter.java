package com.cloudshare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate securityRedisTemplate;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            CustomUserDetailsService customUserDetailsService,
            @Qualifier("securityRedisTemplate") StringRedisTemplate securityRedisTemplate) {
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.securityRedisTemplate = securityRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                ResolvedJwt resolved = (ResolvedJwt) request.getAttribute(ResolvedJwt.REQUEST_ATTRIBUTE);
                if (resolved == null || !jwt.equals(resolved.token())) {
                    resolved = tokenProvider.resolveToken(jwt);
                    if (resolved == null) {
                        // Fallback for mock/legacy provider stubs
                        boolean valid = tokenProvider.validateToken(jwt);
                        String tokenType = valid ? tokenProvider.getTokenType(jwt) : null;
                        String jti = valid ? tokenProvider.getJtiFromToken(jwt) : null;
                        String userIdStr = valid ? tokenProvider.getUserIdFromToken(jwt) : null;
                        resolved = new ResolvedJwt(jwt, valid, userIdStr, tokenType, jti);
                    }
                }

                if (resolved.valid() && "access".equals(resolved.tokenType())) {
                    String jti = resolved.jti();

                    // Verify blacklist status in Redis Security
                    boolean isBlacklisted = false;
                    if (jti != null) {
                        String blacklistKey = "blacklist:token:" + jti;
                        isBlacklisted = Boolean.TRUE.equals(securityRedisTemplate.hasKey(blacklistKey));
                    }

                    if (isBlacklisted) {
                        log.warn("Attempted access with blacklisted JWT access token jti={}", jti);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Token is blacklisted/revoked.\"},\"timestamp\":\""
                                        + java.time.Instant.now() + "\"}");
                        return;
                    }

                    String userIdStr = resolved.userId();
                    UUID userId = UUID.fromString(userIdStr);

                    UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
