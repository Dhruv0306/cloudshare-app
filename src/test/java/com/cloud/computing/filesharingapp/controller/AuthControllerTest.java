package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.config.TestEmailConfig;
import com.cloud.computing.filesharingapp.dto.LoginRequest;
import com.cloud.computing.filesharingapp.dto.SignupRequest;
import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@Import(TestEmailConfig.class)
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSignupSuccess() throws Exception {
        // Given
        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully! Please check your email for verification code."));
    }

    @Test
    void testSignupDuplicateUsername() throws Exception {
        // Given
        User existingUser = new User("testuser", "existing@example.com", passwordEncoder.encode("Password123"));
        userRepository.save(existingUser);

        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Username is already taken!"));
    }

    @Test
    void testSignupDuplicateEmail() throws Exception {
        // Given
        User existingUser = new User("existinguser", "test@example.com", passwordEncoder.encode("Password123"));
        userRepository.save(existingUser);

        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Email is already in use!"));
    }

    @Test
    void testSignupInvalidData() throws Exception {
        // Given
        SignupRequest signupRequest = new SignupRequest("", "", ""); // Invalid data

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSignupWeakPassword() throws Exception {
        // Given
        SignupRequest signupRequest = new SignupRequest("testuser", "test@example.com", "password123"); // Weak password (no uppercase)

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Password does not meet minimum requirements: At least one uppercase letter, At least one special character"));
    }

    @Test
    void testSigninSuccess() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("testuser", "Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void testSigninInvalidCredentials() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("testuser", "wrongpassword");

        // When & Then
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSigninUnverifiedUser() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        // User is not verified (default state)
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("testuser", "Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Please verify your email address before logging in. Check your email for the verification code."));
    }

    @Test
    void testGetUserEmailForUnverifiedUser() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        // User is not verified (default state)
        userRepository.save(user);

        // When & Then
        mockMvc.perform(get("/api/auth/user-email/testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void testGetUserEmailForVerifiedUser() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        // When & Then
        mockMvc.perform(get("/api/auth/user-email/testuser"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: User is already verified!"));
    }

    @Test
    void testGetUserEmailForNonexistentUser() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/auth/user-email/nonexistent"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: User not found!"));
    }

    @Test
    void testSigninNonexistentUser() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest("nonexistent", "Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}