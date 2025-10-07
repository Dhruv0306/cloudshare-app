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
    DOWNLOAD
}