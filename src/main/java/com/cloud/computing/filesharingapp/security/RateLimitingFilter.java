package com.cloud.computing.filesharingapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitingFilter extends OncePerRequestFilter {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Rate limiting configuration
    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final int MAX_RESEND_REQUESTS = 5;
    private static final int RATE_LIMIT_WINDOW_HOURS = 1;
    
    // In-memory storage for rate limiting (in production, use Redis or database)
    private final Map<String, RateLimitInfo> verificationAttempts = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> resendRequests = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Only apply rate limiting to specific verification endpoints
        if ("POST".equals(method)) {
            if (path.equals("/api/auth/verify-email")) {
                if (!checkVerificationRateLimit(request, response)) {
                    return;
                }
            } else if (path.equals("/api/auth/resend-verification")) {
                if (!checkResendRateLimit(request, response)) {
                    return;
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean checkVerificationRateLimit(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String clientId = getClientIdentifier(request);
        RateLimitInfo rateLimitInfo = verificationAttempts.computeIfAbsent(clientId, k -> new RateLimitInfo());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Reset counter if window has passed
        if (rateLimitInfo.getWindowStart().plus(RATE_LIMIT_WINDOW_HOURS, ChronoUnit.HOURS).isBefore(now)) {
            rateLimitInfo.reset(now);
        }
        
        if (rateLimitInfo.getCount() >= MAX_VERIFICATION_ATTEMPTS) {
            sendRateLimitResponse(response, "Too many verification attempts", 
                                MAX_VERIFICATION_ATTEMPTS, RATE_LIMIT_WINDOW_HOURS);
            return false;
        }
        
        rateLimitInfo.increment();
        return true;
    }
    
    private boolean checkResendRateLimit(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String clientId = getClientIdentifier(request);
        RateLimitInfo rateLimitInfo = resendRequests.computeIfAbsent(clientId, k -> new RateLimitInfo());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Reset counter if window has passed
        if (rateLimitInfo.getWindowStart().plus(RATE_LIMIT_WINDOW_HOURS, ChronoUnit.HOURS).isBefore(now)) {
            rateLimitInfo.reset(now);
        }
        
        if (rateLimitInfo.getCount() >= MAX_RESEND_REQUESTS) {
            sendRateLimitResponse(response, "Too many resend requests", 
                                MAX_RESEND_REQUESTS, RATE_LIMIT_WINDOW_HOURS);
            return false;
        }
        
        rateLimitInfo.increment();
        return true;
    }
    
    private String getClientIdentifier(HttpServletRequest request) {
        // Use IP address as client identifier (in production, consider using user ID if authenticated)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    private void sendRateLimitResponse(HttpServletResponse response, String message, 
                                     int maxRequests, int windowHours) throws IOException {
        response.setStatus(429); // HTTP 429 Too Many Requests
        response.setContentType("application/json");
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", message);
        errorResponse.put("maxRequests", maxRequests);
        errorResponse.put("windowHours", windowHours);
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    private static class RateLimitInfo {
        private int count = 0;
        private LocalDateTime windowStart = LocalDateTime.now();
        
        public int getCount() {
            return count;
        }
        
        public LocalDateTime getWindowStart() {
            return windowStart;
        }
        
        public void increment() {
            count++;
        }
        
        public void reset(LocalDateTime newWindowStart) {
            count = 0;
            windowStart = newWindowStart;
        }
    }
}