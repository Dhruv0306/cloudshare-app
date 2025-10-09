package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileShare;

/**
 * Result of share access validation containing validation outcome and details.
 * 
 * <p>This class encapsulates the result of validating access to a shared file,
 * including whether access is allowed, the reason for denial (if applicable),
 * and the associated file share.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class ShareAccessValidationResult {
    
    private final boolean allowed;
    private final String reason;
    private final ShareAccessService.AccessDenialType denialType;
    private final FileShare fileShare;

    /**
     * Private constructor for creating validation results.
     */
    private ShareAccessValidationResult(boolean allowed, String reason, 
                                      ShareAccessService.AccessDenialType denialType, 
                                      FileShare fileShare) {
        this.allowed = allowed;
        this.reason = reason;
        this.denialType = denialType;
        this.fileShare = fileShare;
    }

    /**
     * Creates a validation result indicating access is allowed.
     * 
     * @param fileShare the file share that access is allowed for
     * @return ShareAccessValidationResult indicating allowed access
     */
    public static ShareAccessValidationResult allowed(FileShare fileShare) {
        return new ShareAccessValidationResult(true, null, null, fileShare);
    }

    /**
     * Creates a validation result indicating access is denied.
     * 
     * @param reason the reason for denial
     * @param denialType the type of denial
     * @return ShareAccessValidationResult indicating denied access
     */
    public static ShareAccessValidationResult denied(String reason, ShareAccessService.AccessDenialType denialType) {
        return new ShareAccessValidationResult(false, reason, denialType, null);
    }

    /**
     * Creates a validation result indicating the share is invalid.
     * 
     * @param reason the reason the share is invalid
     * @return ShareAccessValidationResult indicating invalid share
     */
    public static ShareAccessValidationResult invalid(String reason) {
        return new ShareAccessValidationResult(false, reason, ShareAccessService.AccessDenialType.PERMISSION_DENIED, null);
    }

    /**
     * Checks if access is allowed.
     * 
     * @return true if access is allowed, false otherwise
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Gets the reason for denial (if access is not allowed).
     * 
     * @return the reason for denial, or null if access is allowed
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the type of denial (if access is not allowed).
     * 
     * @return the denial type, or null if access is allowed
     */
    public ShareAccessService.AccessDenialType getDenialType() {
        return denialType;
    }

    /**
     * Gets the file share (if access is allowed).
     * 
     * @return the file share, or null if access is not allowed
     */
    public FileShare getFileShare() {
        return fileShare;
    }
}