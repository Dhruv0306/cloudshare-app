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
     * @return true if the share is valid, false otherwise
     */
    public boolean isValid() {
        if (!active) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        if (maxAccess != null && accessCount >= maxAccess) {
            return false;
        }
        return true;
    }

    /**
     * Increments the access count for this share.
     */
    public void incrementAccessCount() {
        this.accessCount++;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileEntity getFile() {
        return file;
    }

    public void setFile(FileEntity file) {
        this.file = file;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getShareToken() {
        return shareToken;
    }

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