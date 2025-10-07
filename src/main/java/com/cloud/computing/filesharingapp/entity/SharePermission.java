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
    /** Recipients can only view the file but cannot download it */
    VIEW_ONLY,
    
    /** Recipients can both view and download the file */
    DOWNLOAD
}