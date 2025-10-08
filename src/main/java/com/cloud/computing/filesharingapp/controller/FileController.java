package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.dto.FileResponse;
import com.cloud.computing.filesharingapp.dto.MessageResponse;
import com.cloud.computing.filesharingapp.dto.ShareRequest;
import com.cloud.computing.filesharingapp.dto.ShareResponse;
import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.SharePermission;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
import com.cloud.computing.filesharingapp.service.FileService;
import com.cloud.computing.filesharingapp.service.FileSharingService;
import com.cloud.computing.filesharingapp.service.ShareNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for file management operations.
 * 
 * <p>
 * This controller provides endpoints for authenticated users to:
 * <ul>
 * <li>Upload files to their personal storage</li>
 * <li>Download their uploaded files</li>
 * <li>List all their files</li>
 * <li>Retrieve specific file metadata</li>
 * <li>Delete their files</li>
 * </ul>
 * 
 * <p>
 * All operations are user-scoped, ensuring users can only access their own
 * files.
 * The controller includes comprehensive logging for security auditing and
 * debugging.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileSharingService fileSharingService;

    @Autowired
    private ShareNotificationService shareNotificationService;

    /**
     * Uploads a file for the authenticated user.
     * 
     * <p>
     * This endpoint accepts multipart file uploads and stores them securely
     * with user isolation. Each file is assigned a unique identifier to prevent
     * naming conflicts and unauthorized access.
     * 
     * @param file           the multipart file to upload
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the created FileEntity or error message
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("File upload request from user: {} (ID: {}), filename: {}, size: {} bytes",
                userPrincipal.getUsername(), userPrincipal.getId(), file.getOriginalFilename(), file.getSize());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            FileEntity fileEntity = fileService.storeFile(file, user);

            logger.info("File uploaded successfully - ID: {}, filename: {}, user: {}",
                    fileEntity.getId(), fileEntity.getOriginalFileName(), user.getUsername());

            return ResponseEntity.ok().body(fileEntity);
        } catch (Exception ex) {
            logger.error("File upload failed for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("Could not upload file: " + ex.getMessage());
        }
    }

    /**
     * Downloads a file by its stored filename for the authenticated user.
     * 
     * <p>
     * This endpoint retrieves files that belong to the authenticated user only.
     * The response includes appropriate headers for file download with the original
     * filename and content type.
     * 
     * @param fileName       the stored filename (UUID-prefixed) of the file to
     *                       download
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the file resource or 404 if not
     *         found/unauthorized
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("File download request from user: {} (ID: {}), filename: {}",
                userPrincipal.getUsername(), userPrincipal.getId(), fileName);

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Optional<FileEntity> fileEntity = fileService.getUserFileByFileName(fileName, user);
            if (fileEntity.isEmpty()) {
                logger.warn("File not found or access denied - filename: {}, user: {}", fileName,
                        userPrincipal.getUsername());
                return ResponseEntity.notFound().build();
            }

            Resource resource = fileService.loadFileAsResource(fileName);
            String contentType = fileEntity.get().getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            logger.info("File downloaded successfully - filename: {}, original: {}, user: {}",
                    fileName, fileEntity.get().getOriginalFileName(), userPrincipal.getUsername());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileEntity.get().getOriginalFileName() + "\"")
                    .body(resource);
        } catch (Exception ex) {
            logger.error("File download failed for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Retrieves all files belonging to the authenticated user with sharing information.
     * 
     * <p>
     * Returns a list of FileResponse objects containing metadata for all files
     * uploaded by the current user. This includes file names, sizes, upload dates,
     * sharing status indicators, share counts, and activity tracking.
     * 
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing a list of the user's files with sharing info
     */
    @GetMapping
    public ResponseEntity<List<FileResponse>> getUserFiles(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.debug("File list request from user: {} (ID: {})", userPrincipal.getUsername(), userPrincipal.getId());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<FileResponse> files = fileService.getUserFilesWithSharingInfo(user);

            logger.info("Retrieved {} files with sharing info for user: {}", files.size(), userPrincipal.getUsername());

            return ResponseEntity.ok(files);
        } catch (Exception ex) {
            logger.error("Failed to retrieve files for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(),
                    ex);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retrieves a specific file by its ID for the authenticated user with sharing information.
     * 
     * <p>
     * Returns the FileResponse metadata for a file with the specified ID,
     * including sharing status, share counts, and activity tracking,
     * but only if the file belongs to the authenticated user.
     * 
     * @param id             the unique identifier of the file
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the FileResponse or 404 if not
     *         found/unauthorized
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileResponse> getUserFileById(@PathVariable Long id, Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Optional<FileResponse> file = fileService.getUserFileByIdWithSharingInfo(id, user);
            return file.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deletes a file by its ID for the authenticated user.
     * 
     * <p>
     * Permanently removes both the file record from the database and the
     * physical file from storage. Only files belonging to the authenticated
     * user can be deleted.
     * 
     * @param id             the unique identifier of the file to delete
     * @param authentication the current user's authentication context
     * @return ResponseEntity with success message or error details
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUserFile(@PathVariable Long id, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("File deletion request from user: {} (ID: {}), file ID: {}",
                userPrincipal.getUsername(), userPrincipal.getId(), id);

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            fileService.deleteUserFile(id, user);

            logger.info("File deleted successfully - ID: {}, user: {}", id, userPrincipal.getUsername());

            return ResponseEntity.ok().body("File deleted successfully");
        } catch (Exception ex) {
            logger.error("File deletion failed for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("Could not delete file: " + ex.getMessage());
        }
    }

    // ========== FILE SHARING ENDPOINTS ==========

    /**
     * Creates a new file share for the specified file.
     * 
     * <p>
     * This endpoint allows authenticated users to create shareable links for their
     * files
     * with configurable permissions, expiration times, and optional email
     * notifications.
     * 
     * @param fileId         the ID of the file to share
     * @param shareRequest   the share configuration parameters
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the ShareResponse with share details and
     *         URL
     */
    @PostMapping("/{fileId}/share")
    public ResponseEntity<?> createFileShare(@PathVariable Long fileId,
            @Valid @RequestBody ShareRequest shareRequest,
            Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("Creating share for file ID: {} by user: {} (ID: {})",
                fileId, userPrincipal.getUsername(), userPrincipal.getId());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ShareResponse shareResponse = fileSharingService.createShare(fileId, shareRequest, user);

            // Send email notifications if requested
            if (shareRequest.isSendNotification() && shareRequest.getRecipientEmails() != null
                    && !shareRequest.getRecipientEmails().isEmpty()) {
                try {
                    // Get the FileShare entity to send notifications
                    Optional<FileShare> fileShareOpt = fileSharingService.getFileShareById(shareResponse.getShareId(),
                            user);
                    if (fileShareOpt.isPresent()) {
                        shareNotificationService.sendShareNotifications(fileShareOpt.get(),
                                shareRequest.getRecipientEmails());
                        logger.info("Share notification emails sent for share ID: {}", shareResponse.getShareId());
                    } else {
                        logger.warn("Could not retrieve FileShare entity for notifications - ID: {}",
                                shareResponse.getShareId());
                    }
                } catch (Exception emailEx) {
                    logger.warn("Failed to send share notification emails for share ID: {} - {}",
                            shareResponse.getShareId(), emailEx.getMessage());
                    // Don't fail the entire request if email sending fails
                }
            }

            logger.info("File share created successfully - ID: {}, token: {}",
                    shareResponse.getShareId(), shareResponse.getShareToken());

            return ResponseEntity.ok(shareResponse);
        } catch (Exception ex) {
            logger.error("Failed to create file share for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(),
                    ex);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Could not create file share: " + ex.getMessage()));
        }
    }

    /**
     * Accesses a shared file by its share token (public endpoint).
     * 
     * <p>
     * This endpoint allows anyone with a valid share token to access file
     * information
     * and preview content. It performs comprehensive validation including token
     * validity,
     * expiration checks, and security controls.
     * 
     * @param token   the share token
     * @param request the HTTP request for IP extraction
     * @return ResponseEntity containing file information or error details
     */
    @GetMapping("/shared/{token}")
    public ResponseEntity<?> accessSharedFile(@PathVariable String token, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.info("Accessing shared file with token: {} from IP: {}", token, clientIp);

        try {
            // Validate share access with comprehensive security checks
            FileSharingService.ShareAccessValidationResult validation = fileSharingService.validateShareAccess(token,
                    clientIp, ShareAccessType.VIEW);

            if (!validation.isAllowed()) {
                logger.warn("Share access denied for token: {} from IP: {} - {}",
                        token, clientIp, validation.getReason());
                return ResponseEntity.status(getHttpStatusForDenial(validation.getDenialType()))
                        .body(new MessageResponse(validation.getReason()));
            }

            FileShare share = validation.getFileShare();

            // Record the access for analytics and security monitoring
            String userAgent = request.getHeader("User-Agent");
            boolean accessRecorded = fileSharingService.recordShareAccess(token, clientIp, userAgent,
                    ShareAccessType.VIEW);

            if (!accessRecorded) {
                logger.error("Failed to record share access for token: {}", token);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new MessageResponse("Failed to process share access"));
            }

            // Build response with file information
            SharedFileAccessResponse response = new SharedFileAccessResponse(
                    share.getFile().getId(),
                    share.getFile().getOriginalFileName(),
                    share.getFile().getContentType(),
                    share.getFile().getFileSize(),
                    share.getPermission(),
                    share.getExpiresAt());

            logger.info("Shared file accessed successfully - file: {}, permission: {}",
                    share.getFile().getOriginalFileName(), share.getPermission());

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            logger.error("Failed to access shared file with token: {} - {}", token, ex.getMessage(), ex);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Could not access shared file: " + ex.getMessage()));
        }
    }

    /**
     * Downloads a shared file by its share token (public endpoint).
     * 
     * <p>
     * This endpoint allows users with valid download permissions to download shared
     * files.
     * It performs comprehensive validation including permission checks and security
     * controls.
     * 
     * @param token   the share token
     * @param request the HTTP request for IP extraction
     * @return ResponseEntity containing the file resource or error details
     */
    @GetMapping("/shared/{token}/download")
    public ResponseEntity<?> downloadSharedFile(@PathVariable String token, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.info("Downloading shared file with token: {} from IP: {}", token, clientIp);

        try {
            // Validate share access with download permission check
            FileSharingService.ShareAccessValidationResult validation = fileSharingService.validateShareAccess(token,
                    clientIp, ShareAccessType.DOWNLOAD);

            if (!validation.isAllowed()) {
                logger.warn("Share download denied for token: {} from IP: {} - {}",
                        token, clientIp, validation.getReason());
                return ResponseEntity.status(getHttpStatusForDenial(validation.getDenialType()))
                        .body(new MessageResponse(validation.getReason()));
            }

            FileShare share = validation.getFileShare();

            // Check if the share permission allows downloads
            if (!share.getPermission().allowsDownload()) {
                logger.warn("Download not allowed for share token: {} - permission: {}",
                        token, share.getPermission());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Download not permitted for this share"));
            }

            // Record the download access
            String userAgent = request.getHeader("User-Agent");
            boolean accessRecorded = fileSharingService.recordShareAccess(token, clientIp, userAgent,
                    ShareAccessType.DOWNLOAD);

            if (!accessRecorded) {
                logger.error("Failed to record share download for token: {}", token);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new MessageResponse("Failed to process share download"));
            }

            // Load and serve the file
            FileEntity file = share.getFile();
            Resource resource = fileService.loadFileAsResource(file.getFileName());
            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            logger.info("Shared file downloaded successfully - file: {}, size: {} bytes",
                    file.getOriginalFileName(), file.getFileSize());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                    .body(resource);
        } catch (Exception ex) {
            logger.error("Failed to download shared file with token: {} - {}", token, ex.getMessage(), ex);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Could not download shared file: " + ex.getMessage()));
        }
    }

    /**
     * Retrieves sharing details for a specific file.
     * 
     * <p>
     * This endpoint allows file owners to view all active shares for their files,
     * including share statistics and access information.
     * 
     * @param fileId         the ID of the file
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing list of shares for the file
     */
    @GetMapping("/{fileId}/shares")
    public ResponseEntity<?> getFileShares(@PathVariable Long fileId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.debug("Retrieving shares for file ID: {} by user: {}", fileId, userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Verify user owns the file
            Optional<FileEntity> fileOptional = fileService.getUserFileById(fileId, user);
            if (fileOptional.isEmpty()) {
                logger.warn("File not found or access denied - ID: {}, user: {}", fileId, userPrincipal.getUsername());
                return ResponseEntity.notFound().build();
            }

            List<ShareResponse> shares = fileSharingService.getUserShares(user).stream()
                    .filter(share -> share.getFileInfo().getFileId().equals(fileId))
                    .toList();

            logger.info("Retrieved {} shares for file ID: {}", shares.size(), fileId);

            return ResponseEntity.ok(shares);
        } catch (Exception ex) {
            logger.error("Failed to retrieve file shares for user: {} - {}", userPrincipal.getUsername(),
                    ex.getMessage(), ex);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Could not retrieve file shares: " + ex.getMessage()));
        }
    }

    /**
     * Updates the permission level of an existing share.
     * 
     * @param shareId        the ID of the share to update
     * @param updateRequest  the new permission settings
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the updated share information
     */
    @PutMapping("/shares/{shareId}")
    public ResponseEntity<?> updateShare(@PathVariable Long shareId,
            @RequestBody SharePermissionUpdateRequest updateRequest,
            Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("Updating share ID: {} by user: {}", shareId, userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Optional<ShareResponse> updatedShare = fileSharingService.updateSharePermission(
                    shareId, updateRequest.getPermission(), user);

            if (updatedShare.isEmpty()) {
                logger.warn("Share not found or access denied - ID: {}, user: {}", shareId,
                        userPrincipal.getUsername());
                return ResponseEntity.notFound().build();
            }

            logger.info("Share updated successfully - ID: {}, new permission: {}",
                    shareId, updateRequest.getPermission());

            return ResponseEntity.ok(updatedShare.get());
        } catch (Exception ex) {
            logger.error("Failed to update share for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(new MessageResponse("Could not update share: " + ex.getMessage()));
        }
    }

    /**
     * Revokes (deactivates) a file share.
     * 
     * @param shareId        the ID of the share to revoke
     * @param authentication the current user's authentication context
     * @return ResponseEntity with success or error message
     */
    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<?> revokeShare(@PathVariable Long shareId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("Revoking share ID: {} by user: {}", shareId, userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            boolean revoked = fileSharingService.revokeShare(shareId, user);

            if (!revoked) {
                logger.warn("Share not found or access denied - ID: {}, user: {}", shareId,
                        userPrincipal.getUsername());
                return ResponseEntity.notFound().build();
            }

            logger.info("Share revoked successfully - ID: {}", shareId);

            return ResponseEntity.ok(new MessageResponse("Share revoked successfully"));
        } catch (Exception ex) {
            logger.error("Failed to revoke share for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(new MessageResponse("Could not revoke share: " + ex.getMessage()));
        }
    }

    /**
     * Retrieves all shares created by the authenticated user.
     * 
     * <p>
     * This endpoint provides a dashboard view of all files shared by the user,
     * including share statistics and access information.
     * 
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing list of user's shares
     */
    @GetMapping("/my-shares")
    public ResponseEntity<?> getUserShares(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.debug("Retrieving all shares for user: {}", userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<ShareResponse> shares = fileSharingService.getUserShares(user);

            logger.info("Retrieved {} shares for user: {}", shares.size(), userPrincipal.getUsername());

            return ResponseEntity.ok(shares);
        } catch (Exception ex) {
            logger.error("Failed to retrieve user shares for user: {} - {}", userPrincipal.getUsername(),
                    ex.getMessage(), ex);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Could not retrieve shares: " + ex.getMessage()));
        }
    }

    // ========== HELPER METHODS ==========

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
     * Maps access denial types to appropriate HTTP status codes.
     * 
     * @param denialType the type of access denial
     * @return the appropriate HTTP status code
     */
    private HttpStatus getHttpStatusForDenial(
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

    // ========== INNER CLASSES ==========

    /**
     * Response DTO for shared file access information.
     * 
     * <p>
     * This class encapsulates all the information needed to display
     * a shared file to users accessing it via a share token, including
     * file metadata and permission details.
     */
    public static class SharedFileAccessResponse {
        private Long fileId;
        private String originalFileName;
        private String contentType;
        private Long fileSize;
        private SharePermission permission;
        private LocalDateTime expiresAt;

        /**
         * Constructs a new SharedFileAccessResponse with the specified parameters.
         * 
         * @param fileId           the unique identifier of the file
         * @param originalFileName the original name of the file
         * @param contentType      the MIME type of the file
         * @param fileSize         the size of the file in bytes
         * @param permission       the permission level for this share
         * @param expiresAt        the expiration date/time of the share
         */
        public SharedFileAccessResponse(Long fileId, String originalFileName, String contentType,
                Long fileSize, SharePermission permission, LocalDateTime expiresAt) {
            this.fileId = fileId;
            this.originalFileName = originalFileName;
            this.contentType = contentType;
            this.fileSize = fileSize;
            this.permission = permission;
            this.expiresAt = expiresAt;
        }

        // Getters and setters with documentation

        /**
         * Gets the unique identifier of the file.
         * 
         * @return the file ID
         */
        public Long getFileId() {
            return fileId;
        }

        /**
         * Sets the unique identifier of the file.
         * 
         * @param fileId the file ID to set
         */
        public void setFileId(Long fileId) {
            this.fileId = fileId;
        }

        /**
         * Gets the original filename as uploaded by the user.
         * 
         * @return the original filename
         */
        public String getOriginalFileName() {
            return originalFileName;
        }

        /**
         * Sets the original filename.
         * 
         * @param originalFileName the original filename to set
         */
        public void setOriginalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
        }

        /**
         * Gets the MIME content type of the file.
         * 
         * @return the content type
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * Sets the MIME content type of the file.
         * 
         * @param contentType the content type to set
         */
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        /**
         * Gets the size of the file in bytes.
         * 
         * @return the file size
         */
        public Long getFileSize() {
            return fileSize;
        }

        /**
         * Sets the size of the file in bytes.
         * 
         * @param fileSize the file size to set
         */
        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        /**
         * Gets the permission level for this share.
         * 
         * @return the share permission
         */
        public SharePermission getPermission() {
            return permission;
        }

        /**
         * Sets the permission level for this share.
         * 
         * @param permission the share permission to set
         */
        public void setPermission(SharePermission permission) {
            this.permission = permission;
        }

        /**
         * Gets the expiration date/time of the share.
         * 
         * @return the expiration date/time, or null if no expiration
         */
        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        /**
         * Sets the expiration date/time of the share.
         * 
         * @param expiresAt the expiration date/time to set
         */
        public void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Request DTO for updating share permissions.
     * 
     * <p>
     * This class represents the request payload for updating the permission
     * level of an existing file share. It includes validation to ensure
     * a valid permission is provided.
     */
    public static class SharePermissionUpdateRequest {

        @jakarta.validation.constraints.NotNull(message = "Permission is required")
        private SharePermission permission;

        /**
         * Gets the new permission level for the share.
         * 
         * @return the share permission
         */
        public SharePermission getPermission() {
            return permission;
        }

        /**
         * Sets the new permission level for the share.
         * 
         * @param permission the share permission to set
         */
        public void setPermission(SharePermission permission) {
            this.permission = permission;
        }
    }
}