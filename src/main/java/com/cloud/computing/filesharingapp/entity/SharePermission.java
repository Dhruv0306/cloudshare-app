package com.cloud.computing.filesharingapp.entity;

/**
 * Enumeration representing the different permission levels for file sharing.
 * 
 * <p>This enum defines what actions recipients can perform when accessing
 * a shared file through a share link.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public enum SharePermission {
    /** 
     * Recipients can only view the file but cannot download it.
     * This permission level allows users to preview file content
     * without giving them the ability to save a local copy.
     */
    VIEW_ONLY,
    
    /** 
     * Recipients can both view and download the file.
     * This permission level provides full access to the shared file,
     * allowing users to both preview and download the content.
     */
    DOWNLOAD;

    /**
     * Checks if this permission level allows downloading files.
     * 
     * <p>This method is used by the access control system to determine
     * whether a download request should be permitted for a given share.
     * 
     * @return true if download is allowed, false otherwise
     */
    public boolean allowsDownload() {
        return this == DOWNLOAD;
    }

    /**
     * Checks if this permission level allows viewing files.
     * 
     * <p>All permission levels currently allow viewing, as this is considered
     * the minimum level of access for any shared file. This method is provided
     * for consistency and future extensibility.
     * 
     * @return true if viewing is allowed (always true for all current permission levels)
     */
    public boolean allowsView() {
        return true;
    }

    /**
     * Returns a human-readable description of this permission level.
     * 
     * @return a descriptive string explaining what this permission allows
     */
    public String getDescription() {
        return switch (this) {
            case VIEW_ONLY -> "Recipients can view the file but cannot download it";
            case DOWNLOAD -> "Recipients can view and download the file";
        };
    }
}