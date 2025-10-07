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

/**
 * Security filter that implements rate limiting for email verification endpoints.
 * 
 * <p>This filter protects against abuse of email verification and resend operations
 * by limiting the number of requests per IP address within a time window. It applies
 * different rate limits for different types of operations:
 * <ul>
 *   <li>Email verification attempts: 3 per hour per IP</li>
 *   <li>Verification code resend requests: 5 per hour per IP</li>
 * </ul>
 * 
 * <p>The filter uses in-memory storage for rate limiting data. In production
 * environments, consider using Redis or a database for distributed rate limiting
 * across multiple application instances.
 * 
 * <p>When rate limits are exceeded, the filter returns HTTP 429 (Too Many Requests)
 * with detailed error information including the limit and time window.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class RateLimitingFilter extends OncePerRequestFilter {
    
    /** JSON object mapper for creating error response payloads */
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /** Maximum number of email verification attempts allowed per time window */
    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    
    /** Maximum number of verification code resend requests allowed per time window */
    private static final int MAX_RESEND_REQUESTS = 5;
    
    /** Time window duration in hours for rate limiting */
    private static final int RATE_LIMIT_WINDOW_HOURS = 1;
    
    /** In-memory storage for verification attempt rate limiting (use Redis in production) */
    private final Map<String, RateLimitInfo> verificationAttempts = new ConcurrentHashMap<>();
    
    /** In-memory storage for resend request rate limiting (use Redis in production) */
    private final Map<String, RateLimitInfo> resendRequests = new ConcurrentHashMap<>();
    
    /**
     * Applies rate limiting to email verification endpoints.
     * 
     * <p>This method intercepts HTTP requests and applies rate limiting only to
     * specific POST endpoints related to email verification:
     * <ul>
     *   <li>/api/auth/verify-email - Email verification attempts</li>
     *   <li>/api/auth/resend-verification - Verification code resend requests</li>
     * </ul>
     * 
     * <p>If rate limits are exceeded, the filter returns an HTTP 429 response
     * and stops further processing. Otherwise, the request continues through
     * the filter chain normally.
     * 
     * @param request the HTTP servlet request
     * @param response the HTTP servlet response
     * @param filterChain the filter chain for continuing request processing
     * @throws ServletException if servlet processing fails
     * @throws IOException if I/O operations fail
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Only apply rate limiting to specific verification endpoints
        if ("POST".equals(method)) {
            if (path.equals("/api/auth/verify-email")) {
                if (!checkVerificationRateLimit(request, response)) {
                    return; // Rate limit exceeded, response already sent
                }
            } else if (path.equals("/api/auth/resend-verification")) {
                if (!checkResendRateLimit(request, response)) {
                    return; // Rate limit exceeded, response already sent
                }
            }
        }
        
        // Continue with the filter chain if rate limits are not exceeded
        filterChain.doFilter(request, response);
    }
    
    /**
     * Checks and enforces rate limits for email verification attempts.
     * 
     * <p>This method tracks verification attempts per client IP address and
     * enforces the configured maximum attempts within the time window. If the
     * limit is exceeded, it sends an HTTP 429 response with error details.
     * 
     * @param request the HTTP servlet request containing client information
     * @param response the HTTP servlet response for sending error responses
     * @return true if the request is within rate limits, false if limit exceeded
     * @throws IOException if response writing fails
     */
    private boolean checkVerificationRateLimit(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String clientId = getClientIdentifier(request);
        RateLimitInfo rateLimitInfo = verificationAttempts.computeIfAbsent(clientId, k -> new RateLimitInfo());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Reset counter if the time window has passed
        if (rateLimitInfo.getWindowStart().plus(RATE_LIMIT_WINDOW_HOURS, ChronoUnit.HOURS).isBefore(now)) {
            rateLimitInfo.reset(now);
        }
        
        // Check if rate limit is exceeded
        if (rateLimitInfo.getCount() >= MAX_VERIFICATION_ATTEMPTS) {
            sendRateLimitResponse(response, "Too many verification attempts", 
                                MAX_VERIFICATION_ATTEMPTS, RATE_LIMIT_WINDOW_HOURS);
            return false;
        }
        
        // Increment the attempt counter
        rateLimitInfo.increment();
        return true;
    }
    
    /**
     * Checks and enforces rate limits for verification code resend requests.
     * 
     * <p>This method tracks resend requests per client IP address and enforces
     * the configured maximum requests within the time window. If the limit is
     * exceeded, it sends an HTTP 429 response with error details.
     * 
     * @param request the HTTP servlet request containing client information
     * @param response the HTTP servlet response for sending error responses
     * @return true if the request is within rate limits, false if limit exceeded
     * @throws IOException if response writing fails
     */
    private boolean checkResendRateLimit(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String clientId = getClientIdentifier(request);
        RateLimitInfo rateLimitInfo = resendRequests.computeIfAbsent(clientId, k -> new RateLimitInfo());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Reset counter if the time window has passed
        if (rateLimitInfo.getWindowStart().plus(RATE_LIMIT_WINDOW_HOURS, ChronoUnit.HOURS).isBefore(now)) {
            rateLimitInfo.reset(now);
        }
        
        // Check if rate limit is exceeded
        if (rateLimitInfo.getCount() >= MAX_RESEND_REQUESTS) {
            sendRateLimitResponse(response, "Too many resend requests", 
                                MAX_RESEND_REQUESTS, RATE_LIMIT_WINDOW_HOURS);
            return false;
        }
        
        // Increment the request counter
        rateLimitInfo.increment();
        return true;
    }
    
    /**
     * Extracts a unique client identifier from the HTTP request.
     * 
     * <p>This method determines the client's IP address for rate limiting purposes.
     * It first checks for the X-Forwarded-For header (common in load balancer setups)
     * and falls back to the direct remote address if not present.
     * 
     * <p>In production environments with authenticated users, consider using user IDs
     * instead of or in addition to IP addresses for more accurate rate limiting.
     * 
     * @param request the HTTP servlet request
     * @return unique client identifier (IP address)
     */
    private String getClientIdentifier(HttpServletRequest request) {
        // Check for X-Forwarded-For header (used by load balancers and proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP address in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        // Fall back to direct remote address
        return request.getRemoteAddr();
    }
    
    /**
     * Sends an HTTP 429 (Too Many Requests) response when rate limits are exceeded.
     * 
     * <p>This method creates a structured JSON error response containing:
     * <ul>
     *   <li>Error type and descriptive message</li>
     *   <li>Maximum allowed requests for the endpoint</li>
     *   <li>Time window duration for the rate limit</li>
     * </ul>
     * 
     * @param response the HTTP servlet response to write to
     * @param message descriptive error message for the specific rate limit
     * @param maxRequests maximum number of requests allowed in the time window
     * @param windowHours duration of the rate limiting time window in hours
     * @throws IOException if response writing fails
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message, 
                                     int maxRequests, int windowHours) throws IOException {
        response.setStatus(429); // HTTP 429 Too Many Requests
        response.setContentType("application/json");
        
        // Create structured error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", message);
        errorResponse.put("maxRequests", maxRequests);
        errorResponse.put("windowHours", windowHours);
        
        // Write JSON response
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    /**
     * Internal class for tracking rate limit information per client.
     * 
     * <p>This class maintains the request count and time window start for each
     * client identifier. It provides methods to increment counters and reset
     * when time windows expire.
     */
    private static class RateLimitInfo {
        /** Current number of requests in the time window */
        private int count = 0;
        
        /** Start time of the current rate limiting window */
        private LocalDateTime windowStart = LocalDateTime.now();
        
        /**
         * Gets the current request count for this client.
         * 
         * @return number of requests made in the current time window
         */
        public int getCount() {
            return count;
        }
        
        /**
         * Gets the start time of the current rate limiting window.
         * 
         * @return LocalDateTime when the current window started
         */
        public LocalDateTime getWindowStart() {
            return windowStart;
        }
        
        /**
         * Increments the request count for this client.
         */
        public void increment() {
            count++;
        }
        
        /**
         * Resets the rate limit tracking for a new time window.
         * 
         * @param newWindowStart the start time for the new window
         */
        public void reset(LocalDateTime newWindowStart) {
            count = 0;
            windowStart = newWindowStart;
        }
    }
}