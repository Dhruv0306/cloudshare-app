package com.cloud.computing.filesharingapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Entity representing email verification records in the file sharing application.
 * 
 * <p>This entity manages the email verification workflow by storing verification
 * codes sent to users during registration or email change processes. Key features:
 * <ul>
 *   <li>Time-limited verification codes (15-minute expiry by default)</li>
 *   <li>One-time use verification codes to prevent replay attacks</li>
 *   <li>Comprehensive indexing for efficient queries and cleanup operations</li>
 *   <li>Association with User entities for tracking verification attempts</li>
 * </ul>
 * 
 * <p>The entity includes several database indexes to optimize common operations:
 * <ul>
 *   <li>Email-based lookups for verification attempts</li>
 *   <li>Expiration-based queries for cleanup operations</li>
 *   <li>Composite indexes for efficient filtering by status and time</li>
 * </ul>
 * 
 * <p>Security considerations:
 * <ul>
 *   <li>Verification codes are hashed before storage</li>
 *   <li>Expired codes are automatically marked as unusable</li>
 *   <li>Rate limiting prevents abuse of verification requests</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "email_verifications", indexes = {
    @Index(name = "idx_email_verification_email", columnList = "email"),
    @Index(name = "idx_email_verification_expires_at", columnList = "expiresAt"),
    @Index(name = "idx_email_verification_created_at", columnList = "createdAt"),
    @Index(name = "idx_email_verification_email_used", columnList = "email, used"),
    @Index(name = "idx_email_verification_expires_used", columnList = "expiresAt, used")
})
public class EmailVerification {
    /** Unique identifier for the verification record */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /** Email address for which verification is being performed */
    @NotBlank
    @Size(max = 100)
    @Email
    @Column(nullable = false)
    private String email;
    
    /** Hashed verification code sent to the user's email */
    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String verificationCode;
    
    /** Timestamp when the verification record was created */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /** Timestamp when the verification code expires */
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    /** Flag indicating whether the verification code has been used */
    @Column(nullable = false)
    private boolean used = false;
    
    /** User associated with this verification attempt */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    /**
     * Default constructor that sets creation and expiration timestamps.
     * Creates a verification record that expires in 15 minutes.
     */
    public EmailVerification() {
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusMinutes(15); // 15 minutes expiry
    }
    
    /**
     * Constructor for creating a verification record with all required fields.
     * 
     * @param email the email address to verify
     * @param verificationCode the hashed verification code
     * @param user the user requesting verification
     */
    public EmailVerification(String email, String verificationCode, User user) {
        this.email = email;
        this.verificationCode = verificationCode;
        this.user = user;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusMinutes(15); // 15 minutes expiry
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getVerificationCode() {
        return verificationCode;
    }
    
    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
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
    
    public boolean isUsed() {
        return used;
    }
    
    public void setUsed(boolean used) {
        this.used = used;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    /**
     * Checks if the verification code has expired.
     * 
     * <p>This utility method compares the current time with the expiration
     * timestamp to determine if the verification code is still valid for use.
     * Expired codes should not be accepted for verification.
     * 
     * @return true if the verification code has expired, false otherwise
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}