package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.dto.JwtResponse;
import com.cloud.computing.filesharingapp.dto.LoginRequest;
import com.cloud.computing.filesharingapp.dto.MessageResponse;
import com.cloud.computing.filesharingapp.dto.SignupRequest;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.cloud.computing.filesharingapp.security.JwtUtils;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
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
    
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for username: {}", loginRequest.getUsername());
        
        try {
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
        
        try {
            // Create new user's account
            User user = new User(signUpRequest.getUsername(),
                    signUpRequest.getEmail(),
                    encoder.encode(signUpRequest.getPassword()));
            
            User savedUser = userRepository.save(user);
            
            logger.info("User registered successfully - ID: {}, Username: {}, Email: {}", 
                       savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
            
            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        } catch (Exception e) {
            logger.error("Error during user registration for username: {} - {}", signUpRequest.getUsername(), e.getMessage(), e);
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Registration failed!"));
        }
    }
}