package com.cloud.computing.filesharingapp.entity;

/**
 * Enumeration representing the different types of access to shared files.
 * 
 * <p>This enum is used for logging and tracking how shared files are accessed
 * by recipients through share links.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public enum ShareAccessType {
    /** User viewed the shared file information or preview */
    VIEW,
    
    /** User downloaded the shared file */
    DOWNLOAD
}