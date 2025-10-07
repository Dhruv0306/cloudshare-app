package com.cloud.computing.filesharingapp.dto;

/**
 * Data Transfer Object for user email responses.
 * 
 * <p>This DTO is used to return a user's email address in API responses,
 * primarily during the email verification workflow. It provides a secure
 * way to return email information without exposing other sensitive user data.
 * 
 * <p>This response is typically used when:
 * <ul>
 *   <li>Helping users identify which email needs verification</li>
 *   <li>Confirming email addresses during verification flows</li>
 *   <li>Providing email information for unverified accounts only</li>
 * </ul>
 * 
 * <p><strong>Security Note:</strong> This should only be used for unverified
 * accounts to maintain user privacy and prevent email enumeration attacks.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class UserEmailResponse {
    /** The user's email address */
    private String email;
    
    /**
     * Default constructor for JSON serialization.
     */
    public UserEmailResponse() {}
    
    /**
     * Constructor for creating a user email response.
     * 
     * @param email the user's email address
     */
    public UserEmailResponse(String email) {
        this.email = email;
    }
    
    /**
     * Gets the user's email address.
     * 
     * @return the email address
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * Sets the user's email address.
     * 
     * @param email the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }
}