package com.cloud.computing.filesharingapp.dto;

import com.cloud.computing.filesharingapp.entity.SharePermission;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for file sharing responses.
 * 
 * <p>This DTO contains information about a created or retrieved file share,
 * including the share URL, permissions, and metadata for client consumption.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class ShareResponse {
    
    /** Unique identifier for the share */
    private Long shareId;
    
    /** Unique token for accessing the share */
    private String shareToken;
    
    /** Complete URL for accessing the shared file */
    private String shareUrl;
    
    /** Permission level for the share */
    private SharePermission permission;
    
    /** Timestamp when the share was created */
    private LocalDateTime createdAt;
    
    /** Expiration timestamp for the share */
    private LocalDateTime expiresAt;
    
    /** Whether the share is currently active */
    private boolean active;
    
    /** Number of times the share has been accessed */
    private int accessCount;
    
    /** Maximum number of accesses allowed */
    private Integer maxAccess;
    
    /** Information about the shared file */
    private FileInfo fileInfo;

    /**
     * Default constructor.
     */
    public ShareResponse() {
    }

    /**
     * Constructor with essential share information.
     * 
     * @param shareId the unique share identifier
     * @param shareToken the share access token
     * @param shareUrl the complete share URL
     * @param permission the share permission level
     */
    public ShareResponse(Long shareId, String shareToken, String shareUrl, SharePermission permission) {
        this.shareId = shareId;
        this.shareToken = shareToken;
        this.shareUrl = shareUrl;
        this.permission = permission;
    }

    /**
     * Gets the unique share identifier.
     * 
     * @return the share ID
     */
    public Long getShareId() {
        return shareId;
    }

    /**
     * Sets the unique share identifier.
     * 
     * @param shareId the share ID to set
     */
    public void setShareId(Long shareId) {
        this.shareId = shareId;
    }

    /**
     * Gets the share access token.
     * 
     * @return the share token
     */
    public String getShareToken() {
        return shareToken;
    }

    /**
     * Sets the share access token.
     * 
     * @param shareToken the share token to set
     */
    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    /**
     * Gets the complete share URL.
     * 
     * @return the share URL
     */
    public String getShareUrl() {
        return shareUrl;
    }

    /**
     * Sets the complete share URL.
     * 
     * @param shareUrl the share URL to set
     */
    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }

    /**
     * Gets the share permission level.
     * 
     * @return the share permission
     */
    public SharePermission getPermission() {
        return permission;
    }

    /**
     * Sets the share permission level.
     * 
     * @param permission the share permission to set
     */
    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    /**
     * Gets the share creation timestamp.
     * 
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the share creation timestamp.
     * 
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the share expiration timestamp.
     * 
     * @return the expiration timestamp
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the share expiration timestamp.
     * 
     * @param expiresAt the expiration timestamp to set
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Checks if the share is currently active.
     * 
     * @return true if the share is active, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the share active status.
     * 
     * @param active the active status to set
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Gets the share access count.
     * 
     * @return the number of times the share has been accessed
     */
    public int getAccessCount() {
        return accessCount;
    }

    /**
     * Sets the share access count.
     * 
     * @param accessCount the access count to set
     */
    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    /**
     * Gets the maximum access count.
     * 
     * @return the maximum access count, or null if no limit is set
     */
    public Integer getMaxAccess() {
        return maxAccess;
    }

    /**
     * Sets the maximum access count.
     * 
     * @param maxAccess the maximum access count to set
     */
    public void setMaxAccess(Integer maxAccess) {
        this.maxAccess = maxAccess;
    }

    /**
     * Gets the shared file information.
     * 
     * @return the file information
     */
    public FileInfo getFileInfo() {
        return fileInfo;
    }

    /**
     * Sets the shared file information.
     * 
     * @param fileInfo the file information to set
     */
    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    /**
     * Nested class representing file information in the share response.
     */
    public static class FileInfo {
        private Long fileId;
        private String originalFileName;
        private String contentType;
        private Long fileSize;

        /**
         * Default constructor.
         */
        public FileInfo() {
        }

        /**
         * Constructor with file details.
         * 
         * @param fileId the file identifier
         * @param originalFileName the original file name
         * @param contentType the file content type
         * @param fileSize the file size in bytes
         */
        public FileInfo(Long fileId, String originalFileName, String contentType, Long fileSize) {
            this.fileId = fileId;
            this.originalFileName = originalFileName;
            this.contentType = contentType;
            this.fileSize = fileSize;
        }

        public Long getFileId() {
            return fileId;
        }

        public void setFileId(Long fileId) {
            this.fileId = fileId;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public void setOriginalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }
    }
}