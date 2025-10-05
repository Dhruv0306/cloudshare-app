package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.config.TestEmailConfig;
import com.cloud.computing.filesharingapp.dto.*;
import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.EmailVerification;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.EmailVerificationRepository;
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

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@Import(TestEmailConfig.class)
class AuthControllerNewEndpointsTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

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
    void testVerifyEmailSuccess() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        User savedUser = userRepository.save(user);

        EmailVerification verification = new EmailVerification();
        verification.setEmail("test@example.com");
        verification.setVerificationCode(passwordEncoder.encode("123456"));
        verification.setUser(savedUser);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        verification.setUsed(false);
        emailVerificationRepository.save(verification);

        VerifyEmailRequest request = new VerifyEmailRequest("test@example.com", "123456");

        // When & Then
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully! You can now log in."));
    }

    @Test
    void testVerifyEmailInvalidCode() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        User savedUser = userRepository.save(user);

        EmailVerification verification = new EmailVerification();
        verification.setEmail("test@example.com");
        verification.setVerificationCode(passwordEncoder.encode("123456"));
        verification.setUser(savedUser);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        verification.setUsed(false);
        emailVerificationRepository.save(verification);

        VerifyEmailRequest request = new VerifyEmailRequest("test@example.com", "654321"); // Wrong code

        // When & Then
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void testResendVerificationSuccess() throws Exception {
        // Given
        User user = new User("testuser", "test@example.com", passwordEncoder.encode("Password123"));
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        userRepository.save(user);

        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");

        // When & Then
        mockMvc.perform(post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification code sent successfully! Please check your email."));
    }

    @Test
    void testResendVerificationUserNotFound() throws Exception {
        // Given
        ResendVerificationRequest request = new ResendVerificationRequest("nonexistent@example.com");

        // When & Then
        mockMvc.perform(post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: User not found with this email address."));
    }

    @Test
    void testCheckPasswordStrengthWeak() throws Exception {
        // Given
        CheckPasswordStrengthRequest request = new CheckPasswordStrengthRequest("password");

        // When & Then
        mockMvc.perform(post("/api/auth/check-password-strength")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("WEAK"))
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.requirements").isArray())
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void testCheckPasswordStrengthMedium() throws Exception {
        // Given
        CheckPasswordStrengthRequest request = new CheckPasswordStrengthRequest("Password123");

        // When & Then
        mockMvc.perform(post("/api/auth/check-password-strength")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("MEDIUM"))
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.requirements").isArray())
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void testCheckPasswordStrengthStrong() throws Exception {
        // Given
        CheckPasswordStrengthRequest request = new CheckPasswordStrengthRequest("Password123!@#");

        // When & Then
        mockMvc.perform(post("/api/auth/check-password-strength")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("STRONG"))
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.requirements").isArray())
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void testVerifyEmailValidationErrors() throws Exception {
        // Given - Invalid email format
        VerifyEmailRequest request = new VerifyEmailRequest("invalid-email", "123456");

        // When & Then
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testVerifyEmailInvalidCodeFormat() throws Exception {
        // Given - Invalid code format (not 6 digits)
        VerifyEmailRequest request = new VerifyEmailRequest("test@example.com", "12345");

        // When & Then
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}