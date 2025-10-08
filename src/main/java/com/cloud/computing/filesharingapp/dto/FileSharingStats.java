package com.cloud.computing.filesharingapp.dto;

import java.time.LocalDateTime;

/**
 * DTO containing sharing statistics and activity information for a file.
 * 
 * <p>This class encapsulates all sharing-related metrics for a file including:
 * <ul>
 *   <li>Share counts (total and active)</li>
 *   <li>Access statistics and activity timestamps</li>
 *   <li>Sharing status indicators</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class FileSharingStats {
    
    private Long fileId;
    private boolean isShared;
    private int totalShares;
    private int activeShares;
    private int totalAccessCount;
    private LocalDateTime lastSharedAt;
    private LocalDateTime lastAccessedAt;
    private boolean hasActiveShares;
    
    /**
     * Default constructor.
     */
    public FileSharingStats() {
        this.isShared = false;
        this.totalShares = 0;
        this.activeShares = 0;
        this.totalAccessCount = 0;
        this.hasActiveShares = false;
    }
    
    // Getters and Setters
    
    public Long getFileId() {
        return fileId;
    }
    
    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
    
    public boolean isShared() {
        return isShared;
    }
    
    public void setShared(boolean shared) {
        isShared = shared;
    }
    
    public int getTotalShares() {
        return totalShares;
    }
    
    public void setTotalShares(int totalShares) {
        this.totalShares = totalShares;
    }
    
    public int getActiveShares() {
        return activeShares;
    }
    
    public void setActiveShares(int activeShares) {
        this.activeShares = activeShares;
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
}