package com.cloud.computing.filesharingapp.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Entity representing an access log entry for file shares.
 * 
 * <p>This entity tracks when and how shared files are accessed, including:
 * <ul>
 *   <li>IP address and user agent of the accessor</li>
 *   <li>Timestamp of the access attempt</li>
 *   <li>Type of access (view or download)</li>
 *   <li>Reference to the file share being accessed</li>
 * </ul>
 * 
 * <p>This information is used for security monitoring, usage analytics,
 * and detecting suspicious activity patterns.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "share_access_logs", indexes = {
    @Index(name = "idx_share_access", columnList = "share_id, accessed_at"),
    @Index(name = "idx_accessor_ip", columnList = "accessor_ip"),
    @Index(name = "idx_accessed_at", columnList = "accessed_at")
})
public class ShareAccess {
    /** Unique identifier for the access log entry */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The file share that was accessed */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    @JsonBackReference
    @NotNull
    private FileShare fileShare;

    /** IP address of the accessor (supports both IPv4 and IPv6) */
    @Size(max = 45)
    @Column(name = "accessor_ip")
    private String accessorIp;

    /** User agent string from the accessor's browser */
    @Lob
    @Column(name = "user_agent")
    private String userAgent;

    /** Timestamp when the access occurred */
    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    /** Type of access performed (view or download) */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    @NotNull
    private ShareAccessType accessType;

    /**
     * Default constructor that initializes the access timestamp.
     */
    public ShareAccess() {
        this.accessedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new access log entry.
     * 
     * @param fileShare the file share being accessed
     * @param accessorIp the IP address of the accessor
     * @param userAgent the user agent string
     * @param accessType the type of access performed
     */
    public ShareAccess(FileShare fileShare, String accessorIp, String userAgent, ShareAccessType accessType) {
        this.fileShare = fileShare;
        this.accessorIp = accessorIp;
        this.userAgent = userAgent;
        this.accessType = accessType;
        this.accessedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileShare getFileShare() {
        return fileShare;
    }

    public void setFileShare(FileShare fileShare) {
        this.fileShare = fileShare;
    }

    public String getAccessorIp() {
        return accessorIp;
    }

    public void setAccessorIp(String accessorIp) {
        this.accessorIp = accessorIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(LocalDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }

    public ShareAccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(ShareAccessType accessType) {
        this.accessType = accessType;
    }
}