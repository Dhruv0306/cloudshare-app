package com.cloud.computing.filesharingapp.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing a user in the file sharing application.
 * 
 * <p>This entity stores user account information including authentication credentials,
 * email verification status, and account status. Each user can own multiple files
 * and have multiple email verification attempts.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Unique username and email constraints</li>
 *   <li>Email verification workflow support</li>
 *   <li>Account status tracking (PENDING, ACTIVE, SUSPENDED, etc.)</li>
 *   <li>One-to-many relationships with files and email verifications</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "users")
public class User {
    /** Unique identifier for the user */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique username for login (max 50 characters) */
    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    private String username;

    /** Unique email address for the user (max 100 characters) */
    @NotBlank
    @Size(max = 100)
    @Email
    @Column(unique = true)
    private String email;

    /** Encrypted password (max 120 characters to accommodate BCrypt hashes) */
    @NotBlank
    @Size(max = 120)
    private String password;

    /** Timestamp when the user account was created */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Flag indicating whether the user's email has been verified */
    @Column(nullable = false)
    private boolean emailVerified = false;

    /** Current status of the user account (PENDING, ACTIVE, SUSPENDED, etc.) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus = AccountStatus.PENDING;

    /** List of files owned by this user */
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<FileEntity> files;

    /** List of email verification attempts for this user */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<EmailVerification> emailVerifications;

    /**
     * Default constructor that initializes the creation timestamp.
     */
    public User() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new user with basic information.
     * 
     * @param username the unique username for the user
     * @param email the user's email address
     * @param password the user's password (should be encrypted before storage)
     */
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<FileEntity> getFiles() {
        return files;
    }

    public void setFiles(List<FileEntity> files) {
        this.files = files;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public List<EmailVerification> getEmailVerifications() {
        return emailVerifications;
    }

    public void setEmailVerifications(List<EmailVerification> emailVerifications) {
        this.emailVerifications = emailVerifications;
    }
}