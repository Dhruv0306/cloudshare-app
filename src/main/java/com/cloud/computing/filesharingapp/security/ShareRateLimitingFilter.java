package com.cloud.computing.filesharingapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced rate limiting filter specifically for public share endpoints.
 * 
 * <p>
 * This filter provides aggressive rate limiting for public share endpoints to
 * prevent
 * abuse and protect against various attack vectors including:
 * <ul>
 * <li>Brute force attacks on share tokens</li>
 * <li>Denial of service attacks through excessive requests</li>
 * <li>Bandwidth abuse through repeated downloads</li>
 * <li>Suspicious activity from single IP addresses</li>
 * </ul>
 * 
 * <p>
 * Rate limiting is applied at multiple levels:
 * <ul>
 * <li>Global rate limit per IP address across all shares</li>
 * <li>Per-share rate limit per IP address</li>
 * <li>Suspicious activity detection and blocking</li>
 * <li>Progressive penalties for repeated violations</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Component
public class ShareRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ShareRateLimitingFilter.class);

    @Value("${app.sharing.max-access-per-ip-per-hour:100}")
    private int maxAccessPerIpPerHour;

    @Value("${app.sharing.max-access-per-share-per-ip-per-hour:20}")
    private int maxAccessPerSharePerIpPerHour;

    @Value("${app.sharing.suspicious-activity-threshold:50}")
    private int suspiciousActivityThreshold;

    @Value("${app.sharing.rate-limit-window-hours:1}")
    private int rateLimitWindowHours;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Thread-safe maps for tracking access attempts
    private final Map<String, IpAccessTracker> globalIpAccess = new ConcurrentHashMap<>();
    private final Map<String, Map<String, IpAccessTracker>> perShareIpAccess = new ConcurrentHashMap<>();
    private final Map<String, SuspiciousActivityTracker> suspiciousIps = new ConcurrentHashMap<>();

    /**
     * Determines if this filter should be applied to the current request.
     * 
     * @param request the HTTP request
     * @return true if the request is not for a share endpoint, false otherwise
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/files/shared/");
    }

    /**
     * Performs rate limiting checks for share endpoint requests.
     * 
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain to continue processing
     * @throws ServletException if servlet processing fails
     * @throws IOException      if I/O operations fail
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIpAddress(request);
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Rate limiting check for share request: {} {} from IP: {}", method, requestURI, clientIp);

        try {
            // Step 1: Check if IP is currently blocked for suspicious activity
            if (isSuspiciousIpBlocked(clientIp)) {
                logger.warn("Blocked request from suspicious IP: {} to {}", clientIp, requestURI);
                sendRateLimitResponse(response, "IP temporarily blocked due to suspicious activity");
                return;
            }

            // Step 2: Extract share token from URL for per-share rate limiting
            String shareToken = extractShareToken(requestURI);

            // Step 3: Check global rate limit for this IP
            if (isGlobalRateLimitExceeded(clientIp)) {
                logger.warn("Global rate limit exceeded for IP: {} (limit: {} per hour)",
                        clientIp, maxAccessPerIpPerHour);
                recordSuspiciousActivity(clientIp, "Global rate limit exceeded");
                sendRateLimitResponse(response, "Rate limit exceeded. Please try again later.");
                return;
            }

            // Step 4: Check per-share rate limit if share token is available
            if (shareToken != null && isPerShareRateLimitExceeded(clientIp, shareToken)) {
                logger.warn("Per-share rate limit exceeded for IP: {} on share: {} (limit: {} per hour)",
                        clientIp, shareToken, maxAccessPerSharePerIpPerHour);
                recordSuspiciousActivity(clientIp, "Per-share rate limit exceeded");
                sendRateLimitResponse(response, "Rate limit exceeded for this share. Please try again later.");
                return;
            }

            // Step 5: Record the access attempt
            recordAccess(clientIp, shareToken);

            // Step 6: Check for suspicious activity patterns
            if (detectSuspiciousActivity(clientIp)) {
                logger.warn("Suspicious activity detected from IP: {}", clientIp);
                recordSuspiciousActivity(clientIp, "Suspicious access pattern detected");
            }

            // Step 7: Continue with request processing
            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            logger.error("Error in share rate limiting filter for request: {} - {}",
                    requestURI, ex.getMessage(), ex);
            // Don't block the request due to filter errors, but log them
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Extracts the share token from the request URI.
     * 
     * @param requestURI the request URI
     * @return the share token if found, null otherwise
     */
    private String extractShareToken(String requestURI) {
        // Pattern: /api/files/shared/{token} or /api/files/shared/{token}/download
        String[] parts = requestURI.split("/");
        if (parts.length >= 5 && "shared".equals(parts[4])) {
            return parts.length > 5 ? parts[5] : null;
        }
        return null;
    }

    /**
     * Checks if the global rate limit is exceeded for an IP address.
     * 
     * @param clientIp the client IP address
     * @return true if rate limit is exceeded, false otherwise
     */
    private boolean isGlobalRateLimitExceeded(String clientIp) {
        IpAccessTracker tracker = globalIpAccess.computeIfAbsent(clientIp, k -> new IpAccessTracker());
        return tracker.getAccessCount() >= maxAccessPerIpPerHour;
    }

    /**
     * Checks if the per-share rate limit is exceeded for an IP address and share.
     * 
     * @param clientIp   the client IP address
     * @param shareToken the share token
     * @return true if rate limit is exceeded, false otherwise
     */
    private boolean isPerShareRateLimitExceeded(String clientIp, String shareToken) {
        Map<String, IpAccessTracker> shareAccess = perShareIpAccess.computeIfAbsent(shareToken,
                k -> new ConcurrentHashMap<>());
        IpAccessTracker tracker = shareAccess.computeIfAbsent(clientIp, k -> new IpAccessTracker());
        return tracker.getAccessCount() >= maxAccessPerSharePerIpPerHour;
    }

    /**
     * Records an access attempt for rate limiting tracking.
     * 
     * @param clientIp   the client IP address
     * @param shareToken the share token (can be null)
     */
    private void recordAccess(String clientIp, String shareToken) {
        // Record global access
        IpAccessTracker globalTracker = globalIpAccess.computeIfAbsent(clientIp, k -> new IpAccessTracker());
        globalTracker.recordAccess();

        // Record per-share access if share token is available
        if (shareToken != null) {
            Map<String, IpAccessTracker> shareAccess = perShareIpAccess.computeIfAbsent(shareToken,
                    k -> new ConcurrentHashMap<>());
            IpAccessTracker shareTracker = shareAccess.computeIfAbsent(clientIp, k -> new IpAccessTracker());
            shareTracker.recordAccess();
        }
    }

    /**
     * Detects suspicious activity patterns from an IP address.
     * 
     * @param clientIp the client IP address
     * @return true if suspicious activity is detected, false otherwise
     */
    private boolean detectSuspiciousActivity(String clientIp) {
        IpAccessTracker tracker = globalIpAccess.get(clientIp);
        if (tracker == null) {
            return false;
        }

        // Check if access count exceeds suspicious activity threshold
        return tracker.getAccessCount() >= suspiciousActivityThreshold;
    }

    /**
     * Records suspicious activity from an IP address.
     * 
     * @param clientIp the client IP address
     * @param reason   the reason for marking as suspicious
     */
    private void recordSuspiciousActivity(String clientIp, String reason) {
        SuspiciousActivityTracker tracker = suspiciousIps.computeIfAbsent(clientIp,
                k -> new SuspiciousActivityTracker());
        tracker.recordSuspiciousActivity(reason);

        logger.warn("Suspicious activity recorded for IP: {} - {}", clientIp, reason);
    }

    /**
     * Checks if an IP is currently blocked due to suspicious activity.
     * 
     * @param clientIp the client IP address
     * @return true if IP is blocked, false otherwise
     */
    private boolean isSuspiciousIpBlocked(String clientIp) {
        SuspiciousActivityTracker tracker = suspiciousIps.get(clientIp);
        return tracker != null && tracker.isBlocked();
    }

    /**
     * Sends a rate limit exceeded response.
     * 
     * @param response the HTTP response
     * @param message  the error message
     * @throws IOException if I/O operations fail
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "3600"); // Suggest retry after 1 hour

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", message);
        errorResponse.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("retryAfter", 3600);

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * Extracts the client IP address from the HTTP request.
     * 
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Tracks access attempts from an IP address with time-based cleanup.
     */
    private class IpAccessTracker {
        private int accessCount = 0;
        private LocalDateTime windowStart = LocalDateTime.now();

        /**
         * Records an access attempt and performs cleanup if needed.
         */
        public synchronized void recordAccess() {
            cleanupIfNeeded();
            accessCount++;
        }

        /**
         * Gets the current access count within the time window.
         * 
         * @return the access count
         */
        public synchronized int getAccessCount() {
            cleanupIfNeeded();
            return accessCount;
        }

        /**
         * Cleans up old access records if the time window has expired.
         */
        private void cleanupIfNeeded() {
            LocalDateTime now = LocalDateTime.now();
            if (ChronoUnit.HOURS.between(windowStart, now) >= rateLimitWindowHours) {
                accessCount = 0;
                windowStart = now;
            }
        }
    }

    /**
     * Tracks suspicious activity from an IP address with blocking capability.
     */
    private class SuspiciousActivityTracker {
        private int suspiciousCount = 0;
        private LocalDateTime firstSuspiciousActivity = null;
        private LocalDateTime blockUntil = null;
        @SuppressWarnings("unused")
        private String lastReason = null;

        /**
         * Records a suspicious activity event.
         * 
         * @param reason the reason for the suspicious activity
         */
        public synchronized void recordSuspiciousActivity(String reason) {
            LocalDateTime now = LocalDateTime.now();

            if (firstSuspiciousActivity == null) {
                firstSuspiciousActivity = now;
            }

            suspiciousCount++;
            lastReason = reason;

            // Block IP for progressively longer periods based on suspicious activity count
            int blockMinutes = Math.min(suspiciousCount * 15, 240); // Max 4 hours
            blockUntil = now.plusMinutes(blockMinutes);

            logger.warn("IP blocked for {} minutes due to suspicious activity: {} (count: {})",
                    blockMinutes, reason, suspiciousCount);
        }

        /**
         * Checks if the IP is currently blocked.
         * 
         * @return true if blocked, false otherwise
         */
        public synchronized boolean isBlocked() {
            if (blockUntil == null) {
                return false;
            }

            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(blockUntil)) {
                // Block period has expired, reset counters
                blockUntil = null;
                suspiciousCount = 0;
                firstSuspiciousActivity = null;
                lastReason = null;
                return false;
            }

            return true;
        }
    }
}