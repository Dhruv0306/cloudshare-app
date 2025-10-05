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
                        .body(new MessageResponse("Error: Please verify your email address before logging in. Check your email for the verification code."));
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
    
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        logger.info("Registration attempt for username: {} and email: {}", signUpRequest.getUsername(), signUpRequest.getEmail());
        
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
        // This will throw PasswordValidationException if password doesn't meet requirements
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
            
            return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email for verification code."));
        } catch (RuntimeException e) {
            logger.error("Error during user registration for username: {} - {}", signUpRequest.getUsername(), e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during user registration for username: {} - {}", signUpRequest.getUsername(), e.getMessage(), e);
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Registration failed!"));
        }
    }
    
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest verifyRequest) {
        logger.info("Email verification attempt for email: {}", verifyRequest.getEmail());
        
        // The service now throws exceptions instead of returning boolean
        // Global exception handler will catch and handle the exceptions
        emailVerificationService.verifyCode(verifyRequest.getEmail(), verifyRequest.getCode());
        
        logger.info("Email verification successful for: {}", verifyRequest.getEmail());
        return ResponseEntity.ok(new MessageResponse("Email verified successfully! You can now log in."));
    }
    
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody ResendVerificationRequest resendRequest) {
        logger.info("Resend verification code request for email: {}", resendRequest.getEmail());
        
        // The service now throws exceptions instead of generic RuntimeException
        // Global exception handler will catch and handle the exceptions
        emailVerificationService.resendVerificationCode(resendRequest.getEmail());
        
        logger.info("Verification code resent successfully to: {}", resendRequest.getEmail());
        return ResponseEntity.ok(new MessageResponse("Verification code sent successfully! Please check your email."));
    }
    
    @PostMapping("/check-password-strength")
    public ResponseEntity<PasswordStrengthResponse> checkPasswordStrength(@Valid @RequestBody CheckPasswordStrengthRequest passwordRequest) {
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
}