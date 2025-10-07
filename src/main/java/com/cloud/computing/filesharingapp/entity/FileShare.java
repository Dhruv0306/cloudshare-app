package com.cloud.computing.filesharingapp.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing a file share in the file sharing application.
 * 
 * <p>This entity stores information about shared files including:
 * <ul>
 *   <li>Unique share token for secure access</li>
 *   <li>Permission levels (view-only or download)</li>
 *   <li>Expiration settings and active status</li>
 *   <li>Access tracking and limits</li>
 *   <li>Relationships to files, owners, and access logs</li>
 * </ul>
 * 
 * <p>Share tokens are UUID-based for security and unpredictability.
 * Each share can have optional expiration times and access limits.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "file_shares", indexes = {
    @Index(name = "idx_share_token", columnList = "shareToken", unique = true),
    @Index(name = "idx_file_owner", columnList = "file_id, owner_id"),
    @Index(name = "idx_expires_at", columnList = "expiresAt")
})
public class FileShare {
    /** Unique identifier for the file share */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The file being shared */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    @JsonBackReference
    @NotNull
    private FileEntity file;

    /** The user who created this share */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonBackReference
    @NotNull
    private User owner;

    /** Unique token for accessing the shared file (UUID-based) */
    @NotBlank
    @Size(max = 36)
    @Column(name = "share_token", unique = true, nullable = false)
    private String shareToken;

    /** Permission level for this share */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private SharePermission permission;

    /** Timestamp when the share was created */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Optional expiration timestamp for the share */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Whether the share is currently active */
    @Column(nullable = false)
    private boolean active = true;

    /** Number of times this share has been accessed */
    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    /** Optional maximum number of accesses allowed */
    @Column(name = "max_access")
    private Integer maxAccess;

    /** List of access logs for this share */
    @OneToMany(mappedBy = "fileShare", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ShareAccess> accessLogs;

    /** List of email notifications sent for this share */
    @OneToMany(mappedBy = "fileShare", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ShareNotification> notifications;

    /**
     * Default constructor that initializes the creation timestamp.
     */
    public FileShare() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new file share with required properties.
     * 
     * @param file the file to be shared
     * @param owner the user creating the share
     * @param shareToken the unique share token
     * @param permission the permission level for the share
     */
    public FileShare(FileEntity file, User owner, String shareToken, SharePermission permission) {
        this.file = file;
        this.owner = owner;
        this.shareToken = shareToken;
        this.permission = permission;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Checks if the share is currently valid (active and not expired).
     * 
     * <p>A share is considered valid if all of the following conditions are met:
     * <ul>
     *   <li>The share is marked as active</li>
     *   <li>The share has not expired (if expiration is set)</li>
     *   <li>The access count has not reached the maximum limit (if limit is set)</li>
     * </ul>
     * 
     * @return true if the share is valid and can be accessed, false otherwise
     */
    public boolean isValid() {
        // Check if share is active
        if (!active) {
            return false;
        }
        
        // Check if share has expired
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        
        // Check if maximum access count has been reached
        if (maxAccess != null && accessCount >= maxAccess) {
            return false;
        }
        
        return true;
    }

    /**
     * Increments the access count for this share.
     * 
     * <p>This method should be called each time the shared file is accessed
     * to maintain accurate usage statistics and enforce access limits.
     * 
     * <p>Note: This method does not check if the maximum access limit has been
     * reached. Use {@link #isValid()} to verify share validity before access.
     */
    public void incrementAccessCount() {
        this.accessCount++;
    }

    // Getters and Setters
    
    /**
     * Gets the unique identifier for this file share.
     * 
     * @return the share ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this file share.
     * 
     * @param id the share ID to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the file being shared.
     * 
     * @return the shared file entity
     */
    public FileEntity getFile() {
        return file;
    }

    /**
     * Sets the file being shared.
     * 
     * @param file the file entity to share
     */
    public void setFile(FileEntity file) {
        this.file = file;
    }

    /**
     * Gets the user who created this share.
     * 
     * @return the share owner
     */
    public User getOwner() {
        return owner;
    }

    /**
     * Sets the user who created this share.
     * 
     * @param owner the share owner to set
     */
    public void setOwner(User owner) {
        this.owner = owner;
    }

    /**
     * Gets the unique share token for accessing this share.
     * 
     * @return the share token
     */
    public String getShareToken() {
        return shareToken;
    }

    /**
     * Sets the unique share token for accessing this share.
     * 
     * @param shareToken the share token to set
     */
    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public SharePermission getPermission() {
        return permission;
    }

    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public Integer getMaxAccess() {
        return maxAccess;
    }

    public void setMaxAccess(Integer maxAccess) {
        this.maxAccess = maxAccess;
    }

    public List<ShareAccess> getAccessLogs() {
        return accessLogs;
    }

    public void setAccessLogs(List<ShareAccess> accessLogs) {
        this.accessLogs = accessLogs;
    }

    public List<ShareNotification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<ShareNotification> notifications) {
        this.notifications = notifications;
    }
}