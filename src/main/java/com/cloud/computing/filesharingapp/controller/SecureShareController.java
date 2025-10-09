package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.dto.MessageResponse;
import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.service.FileService;
import com.cloud.computing.filesharingapp.service.FileSharingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;

/**
 * Secure controller for public file sharing endpoints with enhanced security.
 * 
 * <p>This controller provides secure access to shared files through public endpoints
 * that don't require authentication. It includes comprehensive security measures:
 * <ul>
 *   <li>Share token validation through middleware</li>
 *   <li>Permission-based access control</li>
 *   <li>Rate limiting and abuse prevention</li>
 *   <li>Security headers and HTTPS enforcement</li>
 *   <li>Comprehensive access logging</li>
 * </ul>
 * 
 * <p>All requests to this controller are pre-validated by the ShareTokenValidationFilter,
 * which ensures that only valid, non-expired shares with proper permissions can be accessed.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/files/shared")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
public class SecureShareController {

    private static final Logger logger = LoggerFactory.getLogger(SecureShareController.class);

    @Autowired
    private FileService fileService;

    @Autowired
    private FileSharingService fileSharingService;

    /**
     * Provides secure access to shared file information.
     * 
     * <p>This endpoint allows public access to file metadata for valid shares.
     * The share token is pre-validated by the ShareTokenValidationFilter, so this
     * method can safely assume the share is valid and accessible.
     * 
     * <p>Security features:
     * <ul>
     *   <li>Pre-validated share token through middleware</li>
     *   <li>Permission checks for view access</li>
     *   <li>Access logging for security monitoring</li>
     *   <li>Rate limiting through middleware</li>
     *   <li>Security headers to prevent XSS and clickjacking</li>
     * </ul>
     * 
     * @param token the share token (extracted from URL path)
     * @param request the HTTP request containing validation context
     * @param response the HTTP response for adding security headers
     * @return ResponseEntity containing file information or error details
     */
    @GetMapping("/{token}")
    public ResponseEntity<?> getSharedFileInfo(@PathVariable String token, 
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        
        // Extract pre-validated share from request attributes (set by ShareTokenValidationFilter)
        FileShare share = (FileShare) request.getAttribute("validatedShare");
        String clientIp = (String) request.getAttribute("clientIp");
        
        if (share == null) {
            logger.error("Share validation filter did not set validatedShare attribute for token: {}", token);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Share validation failed"));
        }

        try {
            // Record the access for analytics and security monitoring with enhanced security
            String userAgent = request.getHeader("User-Agent");
            boolean accessRecorded = fileSharingService.recordShareAccess(token, clientIp, userAgent, ShareAccessType.VIEW);

            if (!accessRecorded) {
                logger.error("Failed to record share access for token: {} from IP: {}", token, clientIp);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new MessageResponse("Failed to process share access"));
            }

            // Add additional security headers specific to file sharing
            addFileShareSecurityHeaders(response);

            // Build secure response with file information
            FileEntity file = share.getFile();
            SharedFileInfoResponse fileInfo = new SharedFileInfoResponse(
                file.getId(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize(),
                share.getPermission(),
                share.getExpiresAt(),
                share.getAccessCount(),
                share.getMaxAccess()
            );

            logger.info("Shared file info accessed successfully - file: {}, permission: {}, IP: {}", 
                       file.getOriginalFileName(), share.getPermission(), clientIp);

            return ResponseEntity.ok()
                    .header("X-Share-Token", token) // Include token in response for frontend use
                    .body(fileInfo);

        } catch (Exception ex) {
            logger.error("Failed to process shared file info request for token: {} - {}", token, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to access shared file"));
        }
    }

    /**
     * Provides secure download access for shared files.
     * 
     * <p>This endpoint allows public download of shared files with proper permission validation.
     * The share token and download permission are pre-validated by the ShareTokenValidationFilter.
     * 
     * <p>Security features:
     * <ul>
     *   <li>Pre-validated share token and download permission</li>
     *   <li>Secure file serving with proper headers</li>
     *   <li>Access logging for security monitoring</li>
     *   <li>Rate limiting through middleware</li>
     *   <li>Content-Type validation to prevent XSS</li>
     * </ul>
     * 
     * @param token the share token (extracted from URL path)
     * @param request the HTTP request containing validation context
     * @param response the HTTP response for adding security headers
     * @return ResponseEntity containing the file resource or error details
     */
    @GetMapping("/{token}/download")
    public ResponseEntity<?> downloadSharedFile(@PathVariable String token, 
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        
        // Extract pre-validated share from request attributes (set by ShareTokenValidationFilter)
        FileShare share = (FileShare) request.getAttribute("validatedShare");
        String clientIp = (String) request.getAttribute("clientIp");
        
        if (share == null) {
            logger.error("Share validation filter did not set validatedShare attribute for token: {}", token);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Share validation failed"));
        }

        try {
            // Double-check download permission (should already be validated by filter)
            if (!share.getPermission().allowsDownload()) {
                logger.warn("Download not allowed for share token: {} - permission: {}", 
                           token, share.getPermission());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Download not permitted for this share"));
            }

            // Record the download access with enhanced security monitoring
            String userAgent = request.getHeader("User-Agent");
            boolean accessRecorded = fileSharingService.recordShareAccess(token, clientIp, userAgent, ShareAccessType.DOWNLOAD);

            if (!accessRecorded) {
                logger.error("Failed to record share download for token: {} from IP: {}", token, clientIp);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new MessageResponse("Failed to process share download"));
            }

            // Load and serve the file securely
            FileEntity file = share.getFile();
            Resource resource = fileService.loadFileAsResource(file.getFileName());
            
            // Validate and sanitize content type to prevent XSS
            String contentType = sanitizeContentType(file.getContentType());
            
            // Add security headers for file downloads
            addFileDownloadSecurityHeaders(response, file.getOriginalFileName());

            logger.info("Shared file downloaded successfully - file: {}, size: {} bytes, IP: {}", 
                       file.getOriginalFileName(), file.getFileSize(), clientIp);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + sanitizeFileName(file.getOriginalFileName()) + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getFileSize()))
                    .header("X-Share-Token", token)
                    .body(resource);

        } catch (Exception ex) {
            logger.error("Failed to download shared file with token: {} - {}", token, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Failed to download shared file"));
        }
    }

    /**
     * Adds security headers specific to file sharing responses.
     * 
     * @param response the HTTP response to add headers to
     */
    private void addFileShareSecurityHeaders(HttpServletResponse response) {
        // Prevent caching of file information
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        
        // Add content security policy for file info responses
        response.setHeader("Content-Security-Policy", 
            "default-src 'none'; " +
            "script-src 'none'; " +
            "style-src 'none'; " +
            "img-src 'none'; " +
            "connect-src 'none'; " +
            "font-src 'none'; " +
            "object-src 'none'; " +
            "media-src 'none'; " +
            "frame-src 'none'");
    }

    /**
     * Adds security headers specific to file download responses.
     * 
     * @param response the HTTP response to add headers to
     * @param fileName the name of the file being downloaded
     */
    private void addFileDownloadSecurityHeaders(HttpServletResponse response, String fileName) {
        // Prevent caching of downloaded files
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        
        // Add download-specific security headers
        response.setHeader("X-Download-Options", "noopen");
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        
        // Log the download for security monitoring
        logger.info("File download initiated - filename: {}, timestamp: {}", fileName, LocalDateTime.now());
    }

    /**
     * Sanitizes the content type to prevent XSS attacks through MIME type manipulation.
     * 
     * @param contentType the original content type
     * @return the sanitized content type
     */
    private String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "application/octet-stream";
        }
        
        // Remove any potentially dangerous content types
        String sanitized = contentType.toLowerCase().trim();
        
        // Block potentially dangerous MIME types
        if (sanitized.startsWith("text/html") || 
            sanitized.startsWith("application/javascript") ||
            sanitized.startsWith("text/javascript") ||
            sanitized.contains("script")) {
            logger.warn("Potentially dangerous content type blocked: {}", contentType);
            return "application/octet-stream";
        }
        
        return contentType;
    }

    /**
     * Sanitizes the filename to prevent directory traversal and other attacks.
     * 
     * @param fileName the original filename
     * @return the sanitized filename
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "download";
        }
        
        // Remove path separators and other potentially dangerous characters
        String sanitized = fileName.replaceAll("[/\\\\:*?\"<>|]", "_");
        
        // Limit filename length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        
        return sanitized;
    }

    /**
     * Response DTO for shared file information with security considerations.
     */
    public static class SharedFileInfoResponse {
        private final Long fileId;
        private final String fileName;
        private final String contentType;
        private final Long fileSize;
        private final com.cloud.computing.filesharingapp.entity.SharePermission permission;
        private final LocalDateTime expiresAt;
        private final int accessCount;
        private final Integer maxAccess;

        /**
         * Creates a new SharedFileInfoResponse with the specified file information.
         * 
         * @param fileId the file ID
         * @param fileName the original filename
         * @param contentType the file content type
         * @param fileSize the file size in bytes
         * @param permission the share permission level
         * @param expiresAt the share expiration time
         * @param accessCount the current access count
         * @param maxAccess the maximum allowed accesses
         */
        public SharedFileInfoResponse(Long fileId, String fileName, String contentType, Long fileSize,
                                    com.cloud.computing.filesharingapp.entity.SharePermission permission,
                                    LocalDateTime expiresAt, int accessCount, Integer maxAccess) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.contentType = contentType;
            this.fileSize = fileSize;
            this.permission = permission;
            this.expiresAt = expiresAt;
            this.accessCount = accessCount;
            this.maxAccess = maxAccess;
        }

        // Getters
        public Long getFileId() { return fileId; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public Long getFileSize() { return fileSize; }
        public com.cloud.computing.filesharingapp.entity.SharePermission getPermission() { return permission; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public int getAccessCount() { return accessCount; }
        public Integer getMaxAccess() { return maxAccess; }
    }
}