package com.cloud.computing.filesharingapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for user login requests.
 * 
 * <p>This class represents the request payload for user authentication,
 * containing the username and password credentials. It includes validation
 * annotations to ensure both fields are provided.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class LoginRequest {
    /** Username for authentication (required) */
    @NotBlank(message = "Username is required")
    private String username;
    
    /** Password for authentication (required) */
    @NotBlank(message = "Password is required")
    private String password;
    
    /**
     * Default constructor.
     */
    public LoginRequest() {}
    
    /**
     * Constructor with username and password.
     * 
     * @param username the user's username
     * @param password the user's password
     */
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    /**
     * Gets the username.
     * 
     * @return the username
     */
    public String getUsername() { return username; }
    
    /**
     * Sets the username.
     * 
     * @param username the username to set
     */
    public void setUsername(String username) { this.username = username; }
    
    /**
     * Gets the password.
     * 
     * @return the password
     */
    public String getPassword() { return password; }
    
    /**
     * Sets the password.
     * 
     * @param password the password to set
     */
    public void setPassword(String password) { this.password = password; }
}