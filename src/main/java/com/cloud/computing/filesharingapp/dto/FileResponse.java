package com.cloud.computing.filesharingapp.dto;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import java.time.LocalDateTime;

/**
 * Response DTO for file information with sharing status and activity indicators.
 * 
 * <p>This class extends the basic file information with sharing-related metadata
 * to provide comprehensive file status information including:
 * <ul>
 *   <li>Basic file metadata (name, size, type, upload time)</li>
 *   <li>Sharing status indicators (shared, share count)</li>
 *   <li>Recent sharing activity information</li>
 *   <li>File permissions and capabilities</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class FileResponse {
    
    private Long id;
    private String fileName;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadTime;
    
    // Sharing-related fields
    private boolean isShared;
    private int shareCount;
    private int totalAccessCount;
    private LocalDateTime lastSharedAt;
    private LocalDateTime lastAccessedAt;
    private boolean hasActiveShares;
    
    // File capabilities
    private boolean canShare;
    private boolean canDelete;
    private boolean canDownload;
    
    /**
     * Default constructor.
     */
    public FileResponse() {}
    
    /**
     * Constructor that creates a FileResponse from a FileEntity.
     * 
     * @param fileEntity the FileEntity to convert
     */
    public FileResponse(FileEntity fileEntity) {
        this.id = fileEntity.getId();
        this.fileName = fileEntity.getFileName();
        this.originalFileName = fileEntity.getOriginalFileName();
        this.contentType = fileEntity.getContentType();
        this.fileSize = fileEntity.getFileSize();
        this.uploadTime = fileEntity.getUploadTime();
        
        // Default sharing values (to be populated by service)
        this.isShared = false;
        this.shareCount = 0;
        this.totalAccessCount = 0;
        this.hasActiveShares = false;
        
        // Default capabilities (all true for owner)
        this.canShare = true;
        this.canDelete = true;
        this.canDownload = true;
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
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
    
    public LocalDateTime getUploadTime() {
        return uploadTime;
    }
    
    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }
    
    public boolean isShared() {
        return isShared;
    }
    
    public void setShared(boolean shared) {
        isShared = shared;
    }
    
    public int getShareCount() {
        return shareCount;
    }
    
    public void setShareCount(int shareCount) {
        this.shareCount = shareCount;
    }
    
    public int getTotalAccessCount() {
        return totalAccessCount;
    }
    
    public void setTotalAccessCount(int totalAccessCount) {
        this.totalAccessCount = totalAccessCount;
    }
    
    public LocalDateTime getLastSharedAt() {
        return lastSharedAt;
    }
    
    public void setLastSharedAt(LocalDateTime lastSharedAt) {
        this.lastSharedAt = lastSharedAt;
    }
    
    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }
    
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
    
    public boolean isHasActiveShares() {
        return hasActiveShares;
    }
    
    public void setHasActiveShares(boolean hasActiveShares) {
        this.hasActiveShares = hasActiveShares;
    }
    
    public boolean isCanShare() {
        return canShare;
    }
    
    public void setCanShare(boolean canShare) {
        this.canShare = canShare;
    }
    
    public boolean isCanDelete() {
        return canDelete;
    }
    
    public void setCanDelete(boolean canDelete) {
        this.canDelete = canDelete;
    }
    
    public boolean isCanDownload() {
        return canDownload;
    }
    
    public void setCanDownload(boolean canDownload) {
        this.canDownload = canDownload;
    }
}