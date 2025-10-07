package com.cloud.computing.filesharingapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Data Transfer Object for email verification requests.
 * 
 * <p>This DTO encapsulates the information required to verify a user's email
 * address using a verification code. It includes validation to ensure:
 * <ul>
 *   <li>Email address is provided and in valid format</li>
 *   <li>Verification code is exactly 6 digits</li>
 * </ul>
 * 
 * <p>The verification code is typically sent to the user's email address
 * during registration or when requesting email verification. This DTO is
 * used in the verification endpoint to validate the user's access to the
 * provided email address.
 * 
 * <p>Rate limiting is applied to verification attempts to prevent abuse
 * and brute force attacks on verification codes.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class VerifyEmailRequest {
    /** 
     * Email address to be verified.
     * Must be a valid email format.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;
    
    /** 
     * Six-digit verification code sent to the email address.
     * Must be exactly 6 numeric digits.
     */
    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be exactly 6 digits")
    private String verificationCode;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public VerifyEmailRequest() {}
    
    /**
     * Constructor for creating a verification request with email and code.
     * 
     * @param email the email address to verify
     * @param verificationCode the 6-digit verification code
     */
    public VerifyEmailRequest(String email, String verificationCode) {
        this.email = email;
        this.verificationCode = verificationCode;
    }
    
    /**
     * Gets the email address to be verified.
     * 
     * @return the email address
     */
    public String getEmail() { return email; }
    
    /**
     * Sets the email address to be verified.
     * 
     * @param email the email address to set
     */
    public void setEmail(String email) { this.email = email; }
    
    /**
     * Gets the verification code.
     * 
     * @return the 6-digit verification code
     */
    public String getVerificationCode() { return verificationCode; }
    
    /**
     * Sets the verification code.
     * 
     * @param verificationCode the 6-digit verification code to set
     */
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }
}