package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.FileSharingStats;
import com.cloud.computing.filesharingapp.dto.ShareRequest;
import com.cloud.computing.filesharingapp.dto.ShareResponse;
import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.SharePermission;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for managing file sharing operations.
 * 
 * <p>This service provides comprehensive file sharing functionality including:
 * <ul>
 *   <li>Secure share creation with UUID-based token generation</li>
 *   <li>Share validation with expiration and permission checks</li>
 *   <li>Share permission management (view-only vs download)</li>
 *   <li>Share revocation and cleanup operations</li>
 *   <li>Access tracking and limits enforcement</li>
 * </ul>
 * 
 * <p>All share tokens are generated using UUID for security and unpredictability.
 * The service ensures proper access control and maintains audit trails for all operations.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Transactional
public class FileSharingService {

    private static final Logger logger = LoggerFactory.getLogger(FileSharingService.class);

    @Autowired
    private FileShareRepository fileShareRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private ShareAccessService shareAccessService;

    @Autowired
    private AdvancedSecurityService advancedSecurityService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Creates a new file share with the specified parameters.
     * 
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Validates that the file exists and belongs to the user</li>
     *   <li>Performs advanced security validation and rate limiting</li>
     *   <li>Generates a secure UUID-based share token</li>
     *   <li>Creates the share with specified permissions and expiration</li>
     *   <li>Records security audit events</li>
     *   <li>Returns a complete share response with URL</li>
     * </ul>
     * 
     * @param fileId the ID of the file to share
     * @param shareRequest the share configuration parameters
     * @param owner the user creating the share
     * @param clientIp the IP address of the request
     * @param userAgent the user agent string from the request
     * @return ShareResponse containing the share details and URL
     * @throws RuntimeException if the file is not found, access is denied, or security validation fails
     */
    public ShareResponse createShare(Long fileId, ShareRequest shareRequest, User owner, 
                                   String clientIp, String userAgent) {
        logger.info("Creating share for file ID: {} by user: {} (ID: {}) from IP: {}", 
                   fileId, owner.getUsername(), owner.getId(), clientIp);

        // Advanced security validation
        AdvancedSecurityService.ShareCreationValidationResult securityValidation = 
            advancedSecurityService.validateShareCreation(owner, clientIp, userAgent);
        
        if (!securityValidation.isAllowed()) {
            logger.warn("Share creation denied by security validation - user: {}, IP: {}, reason: {}", 
                       owner.getUsername(), clientIp, securityValidation.getReason());
            throw new RuntimeException("Share creation denied: " + securityValidation.getReason());
        }

        // Rate limiting validation
        RateLimitingService.RateLimitResult rateLimitResult = 
            rateLimitingService.validateShareCreation(owner, clientIp);
        
        if (!rateLimitResult.isAllowed()) {
            logger.warn("Share creation rate limited - user: {}, IP: {}, type: {}", 
                       owner.getUsername(), clientIp, rateLimitResult.getLimitType());
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        // Validate file exists and belongs to user
        Optional<FileEntity> fileOptional = fileRepository.findByIdAndOwner(fileId, owner);
        if (fileOptional.isEmpty()) {
            logger.warn("File not found or access denied - ID: {}, user: {}", fileId, owner.getUsername());
            throw new RuntimeException("File not found or access denied");
        }

        FileEntity file = fileOptional.get();
        
        // Generate secure share token
        String shareToken = generateShareToken();
        
        // Create file share entity
        FileShare fileShare = new FileShare(file, owner, shareToken, shareRequest.getPermission());
        fileShare.setExpiresAt(shareRequest.getExpiresAt());
        fileShare.setMaxAccess(shareRequest.getMaxAccess());
        
        // Save the share
        FileShare savedShare = fileShareRepository.save(fileShare);
        
        // Record security events
        advancedSecurityService.recordShareCreation(owner, savedShare, clientIp, userAgent);
        rateLimitingService.recordShareCreation(owner, clientIp);
        
        logger.info("Share created successfully - ID: {}, token: {}, file: {}", 
                   savedShare.getId(), shareToken, file.getOriginalFileName());

        // Build and return response
        return buildShareResponse(savedShare);
    }

    /**
     * Creates a new file share with the specified parameters (legacy method).
     * 
     * @deprecated Use {@link #createShare(Long, ShareRequest, User, String, String)} for enhanced security
     */
    @Deprecated
    public ShareResponse createShare(Long fileId, ShareRequest shareRequest, User owner) {
        return createShare(fileId, shareRequest, owner, "unknown", "unknown");
    }

    /**
     * Generates a secure UUID-based share token.
     * 
     * <p>This method creates a unique, unpredictable token using UUID.randomUUID()
     * which provides cryptographically strong random values suitable for security purposes.
     * 
     * @return a unique share token string
     */
    public String generateShareToken() {
        String token = UUID.randomUUID().toString();
        logger.debug("Generated share token: {}", token);
        return token;
    }

    /**
     * Validates a share token and returns the associated file share if valid.
     * 
     * <p>This method performs comprehensive validation including:
     * <ul>
     *   <li>Token existence and format validation</li>
     *   <li>Share active status verification</li>
     *   <li>Expiration time checking</li>
     *   <li>Access count limit enforcement</li>
     * </ul>
     * 
     * @param shareToken the share token to validate
     * @return Optional containing the FileShare if valid, empty otherwise
     */
    public Optional<FileShare> validateShareToken(String shareToken) {
        logger.debug("Validating share token: {}", shareToken);

        if (shareToken == null || shareToken.trim().isEmpty()) {
            logger.warn("Invalid share token provided: null or empty");
            return Optional.empty();
        }

        Optional<FileShare> shareOptional = fileShareRepository.findByShareToken(shareToken);
        
        if (shareOptional.isEmpty()) {
            logger.warn("Share not found for token: {}", shareToken);
            return Optional.empty();
        }

        FileShare share = shareOptional.get();
        
        if (!share.isValid()) {
            logger.warn("Share is not valid - ID: {}, active: {}, expired: {}, access count: {}/{}", 
                       share.getId(), share.isActive(), 
                       share.getExpiresAt() != null && LocalDateTime.now().isAfter(share.getExpiresAt()),
                       share.getAccessCount(), share.getMaxAccess());
            return Optional.empty();
        }

        logger.debug("Share token validated successfully - ID: {}", share.getId());
        return Optional.of(share);
    }

    /**
     * Validates share access with comprehensive security checks.
     * 
     * <p>This method performs both token validation and security checks including:
     * <ul>
     *   <li>Standard token and share validation</li>
     *   <li>Advanced security threat assessment</li>
     *   <li>Multi-tier rate limiting validation</li>
     *   <li>Permission validation for the requested access type</li>
     *   <li>Suspicious activity detection and automated response</li>
     * </ul>
     * 
     * @param shareToken the share token to validate
     * @param accessorIp the IP address of the accessor
     * @param userAgent the user agent string from the request
     * @param accessType the type of access requested
     * @return ShareAccessValidationResult containing validation outcome and share details
     */
    public ShareAccessValidationResult validateShareAccess(String shareToken, String accessorIp, 
                                                          String userAgent, ShareAccessType accessType) {
        logger.debug("Validating share access for token: {} from IP: {} for {}", shareToken, accessorIp, accessType);

        // Step 1: Basic token validation - checks if share exists, is active, not expired, and under access limit
        Optional<FileShare> shareOptional = validateShareToken(shareToken);
        if (shareOptional.isEmpty()) {
            return ShareAccessValidationResult.invalid("Share not found, expired, or invalid");
        }

        FileShare share = shareOptional.get();

        // Step 2: Advanced rate limiting validation
        RateLimitingService.RateLimitResult rateLimitResult = 
            rateLimitingService.validateShareAccess(share, accessorIp, accessType, null);
        
        if (!rateLimitResult.isAllowed()) {
            logger.warn("Share access rate limited - token: {}, IP: {}, type: {}", 
                       shareToken, accessorIp, rateLimitResult.getLimitType());
            return ShareAccessValidationResult.denied("Rate limit exceeded. Please try again later.", 
                ShareAccessService.AccessDenialType.RATE_LIMITED);
        }

        // Step 3: Legacy security validation for backward compatibility
        ShareAccessService.AccessValidationResult legacyValidation = 
            shareAccessService.validateAccess(share, accessorIp, accessType);

        if (!legacyValidation.isAllowed()) {
            return ShareAccessValidationResult.denied(legacyValidation.getReason(), legacyValidation.getDenialType());
        }

        // All validations passed - access is allowed
        return ShareAccessValidationResult.allowed(share);
    }

    /**
     * Validates share access with comprehensive security checks (legacy method).
     * 
     * @deprecated Use {@link #validateShareAccess(String, String, String, ShareAccessType)} for enhanced security
     */
    @Deprecated
    public ShareAccessValidationResult validateShareAccess(String shareToken, String accessorIp, ShareAccessType accessType) {
        return validateShareAccess(shareToken, accessorIp, "unknown", accessType);
    }

    /**
     * Records an access to a shared file with comprehensive logging and security checks.
     * 
     * <p>This method should be called whenever a shared file is accessed
     * to maintain accurate usage statistics, enforce access limits, and log security events.
     * 
     * @param shareToken the token of the share being accessed
     * @param accessorIp the IP address of the accessor
     * @param userAgent the user agent string from the request
     * @param accessType the type of access (VIEW or DOWNLOAD)
     * @return true if access was recorded successfully, false if share is invalid
     */
    public boolean recordShareAccess(String shareToken, String accessorIp, String userAgent, ShareAccessType accessType) {
        logger.debug("Recording {} access for share token: {} from IP: {}", accessType, shareToken, accessorIp);

        // Step 1: Validate the share token exists and is still valid
        Optional<FileShare> shareOptional = validateShareToken(shareToken);
        if (shareOptional.isEmpty()) {
            return false;
        }

        FileShare share = shareOptional.get();
        
        // Step 2: Perform comprehensive security validation
        ShareAccessValidationResult validation = validateShareAccess(shareToken, accessorIp, userAgent, accessType);
        
        if (!validation.isAllowed()) {
            logger.warn("Access denied for share token: {} from IP: {} - {}", 
                       shareToken, accessorIp, validation.getReason());
            
            // Record failed access for security monitoring
            advancedSecurityService.recordShareAccess(share, accessType, accessorIp, userAgent, false, validation.getReason());
            return false;
        }

        // Step 3: Log the access attempt for security monitoring, analytics, and audit trails
        shareAccessService.logAccess(share, accessorIp, userAgent, accessType);
        
        // Step 4: Record access in advanced security and rate limiting systems
        advancedSecurityService.recordShareAccess(share, accessType, accessorIp, userAgent, true, null);
        rateLimitingService.recordShareAccess(share, accessorIp, accessType, null);
        
        // Step 5: Increment the share's access counter to track usage and enforce limits
        share.incrementAccessCount();
        fileShareRepository.save(share);

        logger.info("Share access recorded - ID: {}, new count: {}, type: {}, IP: {}", 
                   share.getId(), share.getAccessCount(), accessType, accessorIp);
        
        return true;
    }

    /**
     * Records an access to a shared file and increments the access count.
     * 
     * <p>This method is deprecated. Use the overloaded version with IP and user agent for security.
     * 
     * @param shareToken the token of the share being accessed
     * @return true if access was recorded successfully, false if share is invalid
     * @deprecated Use {@link #recordShareAccess(String, String, String, ShareAccessType)} instead
     */
    @Deprecated
    public boolean recordShareAccess(String shareToken) {
        return recordShareAccess(shareToken, "unknown", "unknown", ShareAccessType.VIEW);
    }

    /**
     * Retrieves all active shares belonging to a specific user.
     * 
     * @param owner the user whose shares to retrieve
     * @return List of ShareResponse objects for the user's active shares
     */
    public List<ShareResponse> getUserShares(User owner) {
        logger.debug("Retrieving shares for user: {} (ID: {})", owner.getUsername(), owner.getId());
        
        List<FileShare> shares = fileShareRepository.findByOwnerAndActiveTrueOrderByCreatedAtDesc(owner);
        
        return shares.stream()
                .map(this::buildShareResponse)
                .toList();
    }

    /**
     * Retrieves a specific share by ID for a user.
     * 
     * @param shareId the ID of the share to retrieve
     * @param owner the user who should own the share
     * @return Optional containing the ShareResponse if found and owned by user
     */
    public Optional<ShareResponse> getUserShare(Long shareId, User owner) {
        logger.debug("Retrieving share ID: {} for user: {}", shareId, owner.getUsername());
        
        Optional<FileShare> shareOptional = fileShareRepository.findByIdAndOwner(shareId, owner);
        
        return shareOptional.map(this::buildShareResponse);
    }

    /**
     * Updates the permission level of an existing share.
     * 
     * @param shareId the ID of the share to update
     * @param newPermission the new permission level
     * @param owner the user who owns the share
     * @return Optional containing the updated ShareResponse if successful
     * @throws RuntimeException if share is not found or access is denied
     */
    public Optional<ShareResponse> updateSharePermission(Long shareId, SharePermission newPermission, User owner) {
        logger.info("Updating permission for share ID: {} to {} by user: {}", 
                   shareId, newPermission, owner.getUsername());

        Optional<FileShare> shareOptional = fileShareRepository.findByIdAndOwner(shareId, owner);
        if (shareOptional.isEmpty()) {
            logger.warn("Share not found or access denied - ID: {}, user: {}", shareId, owner.getUsername());
            return Optional.empty();
        }

        FileShare share = shareOptional.get();
        share.setPermission(newPermission);
        FileShare updatedShare = fileShareRepository.save(share);

        logger.info("Share permission updated successfully - ID: {}, new permission: {}", 
                   shareId, newPermission);

        return Optional.of(buildShareResponse(updatedShare));
    }

    /**
     * Revokes (deactivates) a file share.
     * 
     * @param shareId the ID of the share to revoke
     * @param owner the user who owns the share
     * @return true if the share was successfully revoked, false if not found
     */
    public boolean revokeShare(Long shareId, User owner) {
        logger.info("Revoking share ID: {} by user: {} (ID: {})", 
                   shareId, owner.getUsername(), owner.getId());

        Optional<FileShare> shareOptional = fileShareRepository.findByIdAndOwner(shareId, owner);
        if (shareOptional.isEmpty()) {
            logger.warn("Share not found or access denied - ID: {}, user: {}", shareId, owner.getUsername());
            return false;
        }

        FileShare share = shareOptional.get();
        share.setActive(false);
        fileShareRepository.save(share);

        logger.info("Share revoked successfully - ID: {}", shareId);
        return true;
    }

    /**
     * Deactivates all shares for a specific file.
     * 
     * @param fileId the ID of the file whose shares should be deactivated
     * @param owner the user who owns the file
     * @return the number of shares that were deactivated
     */
    public int revokeAllSharesForFile(Long fileId, User owner) {
        logger.info("Revoking all shares for file ID: {} by user: {}", fileId, owner.getUsername());

        Optional<FileEntity> fileOptional = fileRepository.findByIdAndOwner(fileId, owner);
        if (fileOptional.isEmpty()) {
            logger.warn("File not found or access denied - ID: {}, user: {}", fileId, owner.getUsername());
            return 0;
        }

        FileEntity file = fileOptional.get();
        int deactivatedCount = fileShareRepository.deactivateSharesForFile(file);

        logger.info("Deactivated {} shares for file ID: {}", deactivatedCount, fileId);
        return deactivatedCount;
    }

    /**
     * Performs cleanup of expired and invalid shares.
     * 
     * <p>This method should be called periodically to maintain data integrity
     * by deactivating shares that have expired or reached their access limits.
     * 
     * @return the total number of shares that were cleaned up
     */
    public int cleanupExpiredShares() {
        logger.info("Starting cleanup of expired shares");

        LocalDateTime now = LocalDateTime.now();
        
        // Phase 1: Deactivate shares that have passed their expiration date
        int expiredCount = fileShareRepository.deactivateExpiredShares(now);
        
        // Phase 2: Deactivate shares that have reached their maximum access count limit
        int maxAccessCount = fileShareRepository.deactivateMaxAccessReachedShares();
        
        int totalCleaned = expiredCount + maxAccessCount;
        
        logger.info("Cleanup completed - expired: {}, max access reached: {}, total: {}", 
                   expiredCount, maxAccessCount, totalCleaned);
        
        return totalCleaned;
    }

    /**
     * Builds a ShareResponse DTO from a FileShare entity.
     * 
     * @param fileShare the FileShare entity to convert
     * @return ShareResponse containing the share information
     */
    private ShareResponse buildShareResponse(FileShare fileShare) {
        ShareResponse response = new ShareResponse(
            fileShare.getId(),
            fileShare.getShareToken(),
            buildShareUrl(fileShare.getShareToken()),
            fileShare.getPermission()
        );
        
        response.setCreatedAt(fileShare.getCreatedAt());
        response.setExpiresAt(fileShare.getExpiresAt());
        response.setActive(fileShare.isActive());
        response.setAccessCount(fileShare.getAccessCount());
        response.setMaxAccess(fileShare.getMaxAccess());
        
        // Add file information
        FileEntity file = fileShare.getFile();
        ShareResponse.FileInfo fileInfo = new ShareResponse.FileInfo(
            file.getId(),
            file.getOriginalFileName(),
            file.getContentType(),
            file.getFileSize()
        );
        response.setFileInfo(fileInfo);
        
        return response;
    }

    /**
     * Retrieves a FileShare entity by ID for a specific user.
     * 
     * @param shareId the ID of the share to retrieve
     * @param owner the user who should own the share
     * @return Optional containing the FileShare entity if found and owned by user
     */
    public Optional<FileShare> getFileShareById(Long shareId, User owner) {
        logger.debug("Retrieving FileShare entity ID: {} for user: {}", shareId, owner.getUsername());
        return fileShareRepository.findByIdAndOwner(shareId, owner);
    }

    /**
     * Gets sharing statistics for a specific file.
     * 
     * @param fileId the ID of the file
     * @param owner the user who owns the file
     * @return FileSharingStats containing sharing information for the file
     */
    public FileSharingStats getFileSharingStats(Long fileId, User owner) {
        logger.debug("Getting sharing stats for file ID: {} by user: {}", fileId, owner.getUsername());
        
        // Verify user owns the file
        Optional<FileEntity> fileOptional = fileRepository.findByIdAndOwner(fileId, owner);
        if (fileOptional.isEmpty()) {
            return new FileSharingStats(); // Return empty stats if file not found
        }
        
        FileEntity file = fileOptional.get();
        List<FileShare> shares = fileShareRepository.findByFileOrderByCreatedAtDesc(file);
        
        FileSharingStats stats = new FileSharingStats();
        stats.setFileId(fileId);
        stats.setTotalShares(shares.size());
        
        int activeShares = 0;
        int totalAccessCount = 0;
        LocalDateTime lastSharedAt = null;
        LocalDateTime lastAccessedAt = null;
        
        for (FileShare share : shares) {
            if (share.isActive()) {
                activeShares++;
            }
            totalAccessCount += share.getAccessCount();
            
            if (lastSharedAt == null || share.getCreatedAt().isAfter(lastSharedAt)) {
                lastSharedAt = share.getCreatedAt();
            }
            
            // Get last access time from share access service
            LocalDateTime shareLastAccess = shareAccessService.getLastAccessTime(share);
            if (shareLastAccess != null && (lastAccessedAt == null || shareLastAccess.isAfter(lastAccessedAt))) {
                lastAccessedAt = shareLastAccess;
            }
        }
        
        stats.setActiveShares(activeShares);
        stats.setTotalAccessCount(totalAccessCount);
        stats.setLastSharedAt(lastSharedAt);
        stats.setLastAccessedAt(lastAccessedAt);
        stats.setHasActiveShares(activeShares > 0);
        stats.setShared(shares.size() > 0);
        
        return stats;
    }

    /**
     * Builds a complete share URL from a share token.
     * 
     * @param shareToken the share token
     * @return the complete share URL
     */
    private String buildShareUrl(String shareToken) {
        return baseUrl + "/api/shares/" + shareToken;
    }

    /**
     * Result of share access validation containing outcome and details.
     */
    public static class ShareAccessValidationResult {
        private final boolean allowed;
        private final String reason;
        private final ShareAccessService.AccessDenialType denialType;
        private final FileShare fileShare;

        private ShareAccessValidationResult(boolean allowed, String reason, 
                                          ShareAccessService.AccessDenialType denialType, FileShare fileShare) {
            this.allowed = allowed;
            this.reason = reason;
            this.denialType = denialType;
            this.fileShare = fileShare;
        }

        /**
         * Creates a validation result indicating that access is allowed.
         * 
         * @param fileShare the FileShare entity that access is allowed for
         * @return ShareAccessValidationResult indicating allowed access
         */
        public static ShareAccessValidationResult allowed(FileShare fileShare) {
            return new ShareAccessValidationResult(true, null, null, fileShare);
        }

        /**
         * Creates a validation result indicating that the share is invalid.
         * 
         * @param reason the reason why the share is invalid
         * @return ShareAccessValidationResult indicating invalid share
         */
        public static ShareAccessValidationResult invalid(String reason) {
            return new ShareAccessValidationResult(false, reason, ShareAccessService.AccessDenialType.PERMISSION_DENIED, null);
        }

        /**
         * Creates a validation result indicating that access is denied.
         * 
         * @param reason the reason for access denial
         * @param denialType the type of denial (rate limited, permission denied, etc.)
         * @return ShareAccessValidationResult indicating denied access
         */
        public static ShareAccessValidationResult denied(String reason, ShareAccessService.AccessDenialType denialType) {
            return new ShareAccessValidationResult(false, reason, denialType, null);
        }

        /**
         * Checks if access is allowed.
         * @return true if access is allowed, false otherwise
         */
        public boolean isAllowed() { return allowed; }
        
        /**
         * Gets the reason for access denial.
         * @return the denial reason, or null if access is allowed
         */
        public String getReason() { return reason; }
        
        /**
         * Gets the type of access denial.
         * @return the denial type, or null if access is allowed
         */
        public ShareAccessService.AccessDenialType getDenialType() { return denialType; }
        
        /**
         * Gets the FileShare entity if access is allowed.
         * @return the FileShare entity, or null if access is denied
         */
        public FileShare getFileShare() { return fileShare; }
    }
}