package com.cloud.computing.filesharingapp.dto;

import com.cloud.computing.filesharingapp.entity.SharePermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for file sharing requests.
 * 
 * <p>
 * This DTO encapsulates all the information needed to create a new file share,
 * including permission settings, expiration configuration, and optional email
 * notifications.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class ShareRequest {

    /** The permission level for the share (VIEW_ONLY or DOWNLOAD) */
    @NotNull(message = "Permission is required")
    private SharePermission permission;

    /** Optional expiration timestamp for the share */
    private LocalDateTime expiresAt;

    /** Optional list of email addresses to notify about the share */
    private List<@Email(message = "Invalid email format") String> recipientEmails;

    /** Optional maximum number of accesses allowed */
    @Positive(message = "Maximum access count must be positive")
    private Integer maxAccess;

    /** Whether to send email notifications to recipients */
    private boolean sendNotification = false;

    /**
     * Default constructor.
     */
    public ShareRequest() {
    }

    /**
     * Constructor with required permission parameter.
     * 
     * @param permission the permission level for the share
     */
    public ShareRequest(SharePermission permission) {
        this.permission = permission;
    }

    /**
     * Gets the permission level for the share.
     * 
     * @return the share permission
     */
    public SharePermission getPermission() {
        return permission;
    }

    /**
     * Sets the permission level for the share.
     * 
     * @param permission the share permission to set
     */
    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    /**
     * Gets the expiration timestamp for the share.
     * 
     * @return the expiration timestamp, or null if no expiration is set
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expiration timestamp for the share.
     * 
     * @param expiresAt the expiration timestamp to set
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the list of recipient email addresses.
     * 
     * @return the list of recipient emails
     */
    public List<String> getRecipientEmails() {
        return recipientEmails;
    }

    /**
     * Sets the list of recipient email addresses.
     * 
     * @param recipientEmails the list of recipient emails to set
     */
    public void setRecipientEmails(List<String> recipientEmails) {
        this.recipientEmails = recipientEmails;
    }

    /**
     * Gets the maximum access count for the share.
     * 
     * @return the maximum access count, or null if no limit is set
     */
    public Integer getMaxAccess() {
        return maxAccess;
    }

    /**
     * Sets the maximum access count for the share.
     * 
     * @param maxAccess the maximum access count to set
     */
    public void setMaxAccess(Integer maxAccess) {
        this.maxAccess = maxAccess;
    }

    /**
     * Checks if email notifications should be sent.
     * 
     * @return true if notifications should be sent, false otherwise
     */
    public boolean isSendNotification() {
        return sendNotification;
    }

    /**
     * Sets whether email notifications should be sent.
     * 
     * @param sendNotification true to send notifications, false otherwise
     */
    public void setSendNotification(boolean sendNotification) {
        this.sendNotification = sendNotification;
    }
}