package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.ShareRequest;
import com.cloud.computing.filesharingapp.dto.ShareResponse;
import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
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

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Creates a new file share with the specified parameters.
     * 
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Validates that the file exists and belongs to the user</li>
     *   <li>Generates a secure UUID-based share token</li>
     *   <li>Creates the share with specified permissions and expiration</li>
     *   <li>Returns a complete share response with URL</li>
     * </ul>
     * 
     * @param fileId the ID of the file to share
     * @param shareRequest the share configuration parameters
     * @param owner the user creating the share
     * @return ShareResponse containing the share details and URL
     * @throws RuntimeException if the file is not found or access is denied
     */
    public ShareResponse createShare(Long fileId, ShareRequest shareRequest, User owner) {
        logger.info("Creating share for file ID: {} by user: {} (ID: {})", 
                   fileId, owner.getUsername(), owner.getId());

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
        
        logger.info("Share created successfully - ID: {}, token: {}, file: {}", 
                   savedShare.getId(), shareToken, file.getOriginalFileName());

        // Build and return response
        return buildShareResponse(savedShare);
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
     * Records an access to a shared file and increments the access count.
     * 
     * <p>This method should be called whenever a shared file is accessed
     * to maintain accurate usage statistics and enforce access limits.
     * 
     * @param shareToken the token of the share being accessed
     * @return true if access was recorded successfully, false if share is invalid
     */
    public boolean recordShareAccess(String shareToken) {
        logger.debug("Recording access for share token: {}", shareToken);

        Optional<FileShare> shareOptional = validateShareToken(shareToken);
        if (shareOptional.isEmpty()) {
            return false;
        }

        FileShare share = shareOptional.get();
        share.incrementAccessCount();
        fileShareRepository.save(share);

        logger.info("Share access recorded - ID: {}, new count: {}", 
                   share.getId(), share.getAccessCount());
        
        return true;
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
        
        // Deactivate expired shares
        int expiredCount = fileShareRepository.deactivateExpiredShares(now);
        
        // Deactivate shares that have reached max access
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
     * Builds a complete share URL from a share token.
     * 
     * @param shareToken the share token
     * @return the complete share URL
     */
    private String buildShareUrl(String shareToken) {
        return baseUrl + "/api/shares/" + shareToken;
    }
}