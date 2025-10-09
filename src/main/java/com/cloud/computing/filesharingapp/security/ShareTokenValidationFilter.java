package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.service.FileSharingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security filter for validating share tokens on public file sharing endpoints.
 * 
 * <p>
 * This filter intercepts requests to public share endpoints and performs
 * comprehensive
 * validation including:
 * <ul>
 * <li>Share token format and existence validation</li>
 * <li>Share expiration and permission checks</li>
 * <li>IP-based rate limiting and abuse prevention</li>
 * <li>Security headers enforcement</li>
 * <li>Access logging for security monitoring</li>
 * </ul>
 * 
 * <p>
 * The filter operates on the following URL patterns:
 * <ul>
 * <li>/api/files/shared/{token} - for file access</li>
 * <li>/api/files/shared/{token}/download - for file downloads</li>
 * </ul>
 * 
 * <p>
 * Security features include:
 * <ul>
 * <li>Automatic HTTPS enforcement for share links</li>
 * <li>Security headers to prevent XSS and clickjacking</li>
 * <li>Rate limiting to prevent abuse</li>
 * <li>Suspicious activity detection and blocking</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Component
public class ShareTokenValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ShareTokenValidationFilter.class);

    /**
     * Pattern to match share access URLs and extract tokens.
     * Matches: /api/files/shared/{token} and /api/files/shared/{token}/download
     */
    private static final Pattern SHARE_URL_PATTERN = Pattern.compile(
            "^/api/files/shared/([a-fA-F0-9-]{36})(?:/download)?$");

    @Autowired
    private FileSharingService fileSharingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Determines if this filter should be applied to the current request.
     * 
     * @param request the HTTP request
     * @return true if the request is for a share endpoint, false otherwise
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/files/shared/");
    }

    /**
     * Performs share token validation and security checks for public share
     * endpoints.
     * 
     * <p>
     * This method executes the following validation steps:
     * <ol>
     * <li>Extract and validate share token format</li>
     * <li>Add security headers to prevent XSS and clickjacking</li>
     * <li>Enforce HTTPS for secure share access</li>
     * <li>Validate share token and permissions</li>
     * <li>Perform rate limiting and abuse prevention</li>
     * <li>Set share context for downstream processing</li>
     * </ol>
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

        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIpAddress(request);

        logger.debug("Processing share request: {} {} from IP: {}", method, requestURI, clientIp);

        try {
            // Step 1: Add security headers to all share responses
            addSecurityHeaders(response);

            // Step 2: Enforce HTTPS for share links (configurable)
            if (!enforceHttpsIfRequired(request, response)) {
                return; // Request was redirected to HTTPS
            }

            // Step 3: Extract and validate share token format
            String shareToken = extractShareToken(requestURI);
            if (shareToken == null) {
                logger.warn("Invalid share URL format: {} from IP: {}", requestURI, clientIp);
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid share URL format");
                return;
            }

            // Step 4: Determine access type based on URL path
            ShareAccessType accessType = requestURI.endsWith("/download") ? ShareAccessType.DOWNLOAD
                    : ShareAccessType.VIEW;

            // Step 5: Validate share access with comprehensive security checks
            FileSharingService.ShareAccessValidationResult validation = fileSharingService
                    .validateShareAccess(shareToken, clientIp, accessType);

            if (!validation.isAllowed()) {
                logger.warn("Share access denied for token: {} from IP: {} - {}",
                        shareToken, clientIp, validation.getReason());

                HttpStatus status = mapDenialTypeToHttpStatus(validation.getDenialType());
                sendErrorResponse(response, status, validation.getReason());
                return;
            }

            // Step 6: Set share context for downstream controllers
            FileShare share = validation.getFileShare();
            request.setAttribute("validatedShare", share);
            request.setAttribute("shareToken", shareToken);
            request.setAttribute("accessType", accessType);
            request.setAttribute("clientIp", clientIp);

            logger.debug("Share token validated successfully - ID: {}, file: {}, permission: {}",
                    share.getId(), share.getFile().getOriginalFileName(), share.getPermission());

            // Step 7: Continue with the request processing
            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            logger.error("Error in share token validation filter for request: {} - {}",
                    requestURI, ex.getMessage(), ex);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Share validation failed");
        }
    }

    /**
     * Extracts the share token from the request URI.
     * 
     * @param requestURI the request URI
     * @return the share token if valid format, null otherwise
     */
    private String extractShareToken(String requestURI) {
        Matcher matcher = SHARE_URL_PATTERN.matcher(requestURI);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Adds comprehensive security headers to prevent various attacks.
     * 
     * <p>
     * Security headers added:
     * <ul>
     * <li>X-Content-Type-Options: nosniff - prevents MIME type sniffing</li>
     * <li>X-Frame-Options: DENY - prevents clickjacking attacks</li>
     * <li>X-XSS-Protection: 1; mode=block - enables XSS protection</li>
     * <li>Content-Security-Policy: strict policy to prevent XSS</li>
     * <li>Referrer-Policy: no-referrer - prevents referrer leakage</li>
     * <li>Cache-Control: no-cache, no-store - prevents caching of sensitive
     * content</li>
     * </ul>
     * 
     * @param response the HTTP response to add headers to
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking attacks
        response.setHeader("X-Frame-Options", "DENY");

        // Enable XSS protection
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Content Security Policy to prevent XSS
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data:; " +
                        "font-src 'self'; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'none'");

        // Prevent referrer information leakage
        response.setHeader("Referrer-Policy", "no-referrer");

        // Prevent caching of sensitive share content
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // Indicate that the response should be treated as sensitive
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive, nosnippet");
    }

    /**
     * Enforces HTTPS for share links if configured to do so.
     * 
     * @param request  the HTTP request
     * @param response the HTTP response
     * @return true if request can continue, false if redirected to HTTPS
     * @throws IOException if I/O operations fail
     */
    private boolean enforceHttpsIfRequired(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Check if HTTPS enforcement is enabled (from application properties)
        // For now, we'll skip HTTPS enforcement in development mode
        // In production, this should be handled by a load balancer or reverse proxy

        boolean requireHttps = Boolean.parseBoolean(
                System.getProperty("app.sharing.require-https", "false"));

        if (requireHttps && !request.isSecure()) {
            String httpsUrl = "https://" + request.getServerName() +
                    (request.getServerPort() != 80 ? ":" + request.getServerPort() : "") +
                    request.getRequestURI() +
                    (request.getQueryString() != null ? "?" + request.getQueryString() : "");

            logger.info("Redirecting to HTTPS: {} -> {}", request.getRequestURL(), httpsUrl);
            response.sendRedirect(httpsUrl);
            return false;
        }

        return true;
    }

    /**
     * Maps access denial types to appropriate HTTP status codes.
     * 
     * @param denialType the type of access denial
     * @return the appropriate HTTP status code
     */
    private HttpStatus mapDenialTypeToHttpStatus(
            com.cloud.computing.filesharingapp.service.ShareAccessService.AccessDenialType denialType) {

        if (denialType == null) {
            return HttpStatus.BAD_REQUEST;
        }

        return switch (denialType) {
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case SUSPICIOUS_ACTIVITY -> HttpStatus.FORBIDDEN;
        };
    }

    /**
     * Sends a JSON error response with the specified status and message.
     * 
     * @param response the HTTP response
     * @param status   the HTTP status code
     * @param message  the error message
     * @throws IOException if I/O operations fail
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", message);
        errorResponse.put("status", status.value());
        errorResponse.put("timestamp", System.currentTimeMillis());

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * Extracts the client IP address from the HTTP request, considering proxy
     * headers.
     * 
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for X-Forwarded-For header (common with load balancers and proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header (used by some proxies)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fall back to remote address
        return request.getRemoteAddr();
    }
}