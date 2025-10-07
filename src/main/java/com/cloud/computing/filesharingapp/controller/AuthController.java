package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.dto.*;
import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.cloud.computing.filesharingapp.security.JwtUtils;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
import com.cloud.computing.filesharingapp.service.EmailVerificationService;
import com.cloud.computing.filesharingapp.service.PasswordStrengthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication and user management operations.
 * 
 * <p>This controller provides endpoints for:
 * <ul>
 *   <li>User registration with email verification</li>
 *   <li>User authentication and JWT token generation</li>
 *   <li>Email verification workflow</li>
 *   <li>Password strength validation</li>
 *   <li>Verification code resending</li>
 * </ul>
 * 
 * <p>The controller implements a secure registration flow that requires
 * email verification before users can access protected resources. It includes
 * comprehensive logging for security auditing and debugging purposes.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    EmailVerificationService emailVerificationService;

    @Autowired
    PasswordStrengthService passwordStrengthService;

    /**
     * Authenticates a user and returns a JWT token.
     * 
     * <p>This endpoint performs the following validations:
     * <ul>
     *   <li>Verifies user credentials against the database</li>
     *   <li>Ensures the user's email has been verified</li>
     *   <li>Generates a JWT token for authenticated sessions</li>
     * </ul>
     * 
     * <p>Users with unverified emails are rejected and prompted to complete
     * the email verification process before logging in.
     * 
     * @param loginRequest the login credentials (username and password)
     * @return ResponseEntity containing JWT token and user info or error message
     */
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for username: {}", loginRequest.getUsername());

        try {
            // First check if user exists and get their verification status
            User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);
            if (user != null && !user.isEmailVerified()) {
                logger.warn("Login attempt by unverified user: {}", loginRequest.getUsername());
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse(
                                "Error: Please verify your email address before logging in. Check your email for the verification code."));
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();

            logger.info("Successful login for user: {} (ID: {})", userDetails.getUsername(), userDetails.getId());

            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail()));
        } catch (Exception e) {
            logger.warn("Failed login attempt for username: {} - {}", loginRequest.getUsername(), e.getMessage());
            throw e;
        }
    }

    /**
     * Registers a new user account with email verification.
     * 
     * <p>This endpoint performs the following operations:
     * <ul>
     *   <li>Validates username and email uniqueness</li>
     *   <li>Validates password strength requirements</li>
     *   <li>Creates user account with PENDING status</li>
     *   <li>Sends email verification code</li>
     * </ul>
     * 
     * <p>New users cannot access protected resources until they verify
     * their email address using the sent verification code.
     * 
     * @param signUpRequest the registration details (username, email, password)
     * @return ResponseEntity with success message or error details
     */
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        logger.info("Registration attempt for username: {} and email: {}", signUpRequest.getUsername(),
                signUpRequest.getEmail());

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            logger.warn("Registration failed - username already exists: {}", signUpRequest.getUsername());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            logger.warn("Registration failed - email already exists: {}", signUpRequest.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Validate password strength before creating user
        // This will throw PasswordValidationException if password doesn't meet
        // requirements
        passwordStrengthService.validatePasswordRequirements(signUpRequest.getPassword());

        try {
            // Create new user's account with PENDING status
            User user = new User(signUpRequest.getUsername(),
                    signUpRequest.getEmail(),
                    encoder.encode(signUpRequest.getPassword()));
            user.setAccountStatus(AccountStatus.PENDING);
            user.setEmailVerified(false);

            User savedUser = userRepository.save(user);

            // Send verification email
            emailVerificationService.createVerificationRecord(savedUser);

            logger.info("User registered successfully with pending status - ID: {}, Username: {}, Email: {}",
                    savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());

            return ResponseEntity.ok(new MessageResponse(
                    "User registered successfully! Please check your email for verification code."));
        } catch (RuntimeException e) {
            logger.error("Error during user registration for username: {} - {}", signUpRequest.getUsername(),
                    e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during user registration for username: {} - {}", signUpRequest.getUsername(),
                    e.getMessage(), e);
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Registration failed!"));
        }
    }

    /**
     * Verifies a user's email address using the provided verification code.
     * 
     * <p>This endpoint validates the 6-digit verification code sent to the
     * user's email address. Upon successful verification:
     * <ul>
     *   <li>User's email verification status is set to true</li>
     *   <li>Account status is changed to ACTIVE</li>
     *   <li>User can now log in and access protected resources</li>
     * </ul>
     * 
     * @param verifyRequest the verification details (email and code)
     * @return ResponseEntity with success message or error details
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest verifyRequest) {
        logger.info("Email verification attempt for email: {}, code length: {}",
                verifyRequest.getEmail(),
                verifyRequest.getVerificationCode() != null ? verifyRequest.getVerificationCode().length() : "null");

        // Debug logging to see what's being received
        logger.debug("Received verification request - Email: '{}', Code: '{}'",
                verifyRequest.getEmail(), verifyRequest.getVerificationCode());

        // The service now throws exceptions instead of returning boolean
        // Global exception handler will catch and handle the exceptions
        emailVerificationService.verifyCode(verifyRequest.getEmail(), verifyRequest.getVerificationCode());

        logger.info("Email verification successful for: {}", verifyRequest.getEmail());
        return ResponseEntity.ok(new MessageResponse("Email verified successfully! You can now log in."));
    }

    /**
     * Resends the email verification code to a user.
     * 
     * <p>This endpoint allows users to request a new verification code if:
     * <ul>
     *   <li>The original code has expired</li>
     *   <li>The email was not received</li>
     *   <li>The code was lost or deleted</li>
     * </ul>
     * 
     * <p>Rate limiting is applied to prevent abuse of this endpoint.
     * 
     * @param resendRequest the request containing the user's email address
     * @return ResponseEntity with success message or error details
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody ResendVerificationRequest resendRequest) {
        logger.info("Resend verification code request for email: {}", resendRequest.getEmail());

        // The service now throws exceptions instead of generic RuntimeException
        // Global exception handler will catch and handle the exceptions
        emailVerificationService.resendVerificationCode(resendRequest.getEmail());

        logger.info("Verification code resent successfully to: {}", resendRequest.getEmail());
        return ResponseEntity.ok(new MessageResponse("Verification code sent successfully! Please check your email."));
    }

    /**
     * Evaluates the strength of a password and provides feedback.
     * 
     * <p>This endpoint analyzes password strength based on:
     * <ul>
     *   <li>Length requirements</li>
     *   <li>Character complexity (uppercase, lowercase, numbers, symbols)</li>
     *   <li>Common password patterns</li>
     *   <li>Dictionary word detection</li>
     * </ul>
     * 
     * <p>The response includes a strength score and specific recommendations
     * for improving password security.
     * 
     * @param passwordRequest the request containing the password to evaluate
     * @return ResponseEntity with password strength analysis
     */
    @PostMapping("/check-password-strength")
    public ResponseEntity<PasswordStrengthResponse> checkPasswordStrength(
            @Valid @RequestBody CheckPasswordStrengthRequest passwordRequest) {
        logger.debug("Password strength check requested");

        try {
            PasswordStrengthResponse response = passwordStrengthService.evaluatePassword(passwordRequest.getPassword());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during password strength evaluation - {}", e.getMessage());
            // Return a default weak response in case of error
            PasswordStrengthResponse errorResponse = passwordStrengthService.evaluatePassword("");
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Retrieves the email address for a user (for verification purposes only).
     * 
     * <p>This endpoint is used during the verification flow to help users
     * identify which email address needs verification. It only returns email
     * addresses for unverified users to maintain privacy and security.
     * 
     * @param username the username to look up
     * @return ResponseEntity with email address or error message
     */
    @GetMapping("/user-email/{username}")
    public ResponseEntity<?> getUserEmail(@PathVariable String username) {
        logger.info("Get user email request for username: {}", username);

        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                logger.warn("User not found for username: {}", username);
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: User not found!"));
            }

            // Only return email for unverified users to help with verification flow
            if (!user.isEmailVerified()) {
                return ResponseEntity.ok(new UserEmailResponse(user.getEmail()));
            } else {
                logger.warn("Attempt to get email for verified user: {}", username);
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: User is already verified!"));
            }
        } catch (Exception e) {
            logger.error("Error getting user email for username: {} - {}", username, e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Failed to get user email!"));
        }
    }

    /**
     * Debug endpoint for troubleshooting email verification issues.
     * 
     * <p><strong>Note:</strong> This endpoint should be removed in production.
     * It's used for debugging payload structure and data type issues during
     * development and testing.
     * 
     * @param payload the raw request payload for analysis
     * @return ResponseEntity with payload analysis information
     */
    @PostMapping("/debug-verify")
    public ResponseEntity<?> debugVerifyEmail(@RequestBody Map<String, Object> payload) {
        logger.info("Debug verification endpoint called with payload: {}", payload);

        Map<String, Object> response = new HashMap<>();
        response.put("receivedPayload", payload);
        response.put("emailValue", payload.get("email"));
        response.put("codeValue", payload.get("code"));
        response.put("emailType",
                payload.get("email") != null ? payload.get("email").getClass().getSimpleName() : "null");
        response.put("codeType", payload.get("code") != null ? payload.get("code").getClass().getSimpleName() : "null");

        return ResponseEntity.ok(response);
    }
}