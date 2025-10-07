package com.cloud.computing.filesharingapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for user registration requests.
 * 
 * <p>This DTO encapsulates the information required for new user registration
 * and includes comprehensive validation annotations to ensure data integrity:
 * <ul>
 *   <li>Username: 3-20 characters, alphanumeric and underscores only</li>
 *   <li>Email: Valid email format, maximum 50 characters</li>
 *   <li>Password: 8-40 characters (additional strength validation applied separately)</li>
 * </ul>
 * 
 * <p>The validation constraints help prevent common security issues and ensure
 * consistent data quality across the application. Additional password strength
 * validation is performed by the PasswordStrengthService during registration.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class SignupRequest {
    /** 
     * Username for the new account.
     * Must be 3-20 characters and contain only letters, numbers, and underscores.
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;
    
    /** 
     * Email address for the new account.
     * Must be a valid email format and not exceed 50 characters.
     */
    @NotBlank(message = "Email is required")
    @Size(max = 50, message = "Email must not exceed 50 characters")
    @Email(message = "Please provide a valid email address")
    private String email;
    
    /** 
     * Password for the new account.
     * Must be 8-40 characters (additional strength validation applied separately).
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 40, message = "Password must be between 8 and 40 characters")
    private String password;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SignupRequest() {}
    
    /**
     * Constructor for creating a signup request with all required fields.
     * 
     * @param username the desired username for the account
     * @param email the email address for the account
     * @param password the password for the account
     */
    public SignupRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
    
    /**
     * Gets the username for the signup request.
     * 
     * @return the username
     */
    public String getUsername() { return username; }
    
    /**
     * Sets the username for the signup request.
     * 
     * @param username the username to set
     */
    public void setUsername(String username) { this.username = username; }
    
    /**
     * Gets the email address for the signup request.
     * 
     * @return the email address
     */
    public String getEmail() { return email; }
    
    /**
     * Sets the email address for the signup request.
     * 
     * @param email the email address to set
     */
    public void setEmail(String email) { this.email = email; }
    
    /**
     * Gets the password for the signup request.
     * 
     * @return the password
     */
    public String getPassword() { return password; }
    
    /**
     * Sets the password for the signup request.
     * 
     * @param password the password to set
     */
    public void setPassword(String password) { this.password = password; }
}