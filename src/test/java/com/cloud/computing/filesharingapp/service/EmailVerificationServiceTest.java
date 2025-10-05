package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.EmailVerification;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.EmailVerificationRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    private User testUser;
    private EmailVerification testVerification;

    @BeforeEach
    void setUp() {
        // Set up configuration values
        ReflectionTestUtils.setField(emailVerificationService, "maxAttemptsPerCode", 3);
        ReflectionTestUtils.setField(emailVerificationService, "maxCodesPerHour", 5);
        ReflectionTestUtils.setField(emailVerificationService, "codeExpiryMinutes", 15);

        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setEmailVerified(false);
        testUser.setAccountStatus(AccountStatus.PENDING);

        // Create test verification
        testVerification = new EmailVerification();
        testVerification.setId(1L);
        testVerification.setEmail("test@example.com");
        testVerification.setVerificationCode("hashedCode123");
        testVerification.setUser(testUser);
        testVerification.setCreatedAt(LocalDateTime.now());
        testVerification.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        testVerification.setUsed(false);
    }

    @Test
    void generateVerificationCode_ShouldReturnSixDigitCode() {
        String code = emailVerificationService.generateVerificationCode();
        
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void createVerificationRecord_ShouldCreateAndSendEmail() {
        // Arrange
        when(emailVerificationRepository.countByEmailAndCreatedAtAfterAndUsedFalse(anyString(), any(LocalDateTime.class)))
            .thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedCode");
        when(emailVerificationRepository.save(any(EmailVerification.class))).thenReturn(testVerification);

        // Act
        emailVerificationService.createVerificationRecord(testUser);

        // Assert
        verify(emailVerificationRepository).markAllAsUsedByEmail(testUser.getEmail());
        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendVerificationEmail(eq(testUser.getEmail()), anyString());
    }

    @Test
    void createVerificationRecord_ShouldThrowExceptionWhenRateLimitExceeded() {
        // Arrange
        when(emailVerificationRepository.countByEmailAndCreatedAtAfterAndUsedFalse(anyString(), any(LocalDateTime.class)))
            .thenReturn(5L);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> emailVerificationService.createVerificationRecord(testUser));
        
        assertTrue(exception.getMessage().contains("Too many verification code requests"));
        verify(emailVerificationRepository, never()).save(any(EmailVerification.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void verifyCode_ShouldSucceedForValidCode() {
        // Arrange
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.of(testVerification));
        when(passwordEncoder.matches("123456", "hashedCode123")).thenReturn(true);
        when(emailVerificationRepository.save(any(EmailVerification.class))).thenReturn(testVerification);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act & Assert
        assertDoesNotThrow(() -> emailVerificationService.verifyCode("test@example.com", "123456"));
        
        verify(emailVerificationRepository).save(testVerification);
        verify(userRepository).save(testUser);
        assertTrue(testVerification.isUsed());
        assertTrue(testUser.isEmailVerified());
        assertEquals(AccountStatus.ACTIVE, testUser.getAccountStatus());
    }

    @Test
    void verifyCode_ShouldThrowExceptionForInvalidCode() {
        // Arrange
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.of(testVerification));
        when(passwordEncoder.matches("wrong123", "hashedCode123")).thenReturn(false);

        // Act & Assert
        assertThrows(Exception.class, () -> emailVerificationService.verifyCode("test@example.com", "wrong123"));
        
        verify(userRepository, never()).save(any(User.class));
        assertFalse(testVerification.isUsed());
    }

    @Test
    void verifyCode_ShouldThrowExceptionForExpiredCode() {
        // Arrange
        testVerification.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.of(testVerification));
        when(emailVerificationRepository.save(any(EmailVerification.class))).thenReturn(testVerification);

        // Act & Assert
        assertThrows(Exception.class, () -> emailVerificationService.verifyCode("test@example.com", "123456"));
        
        verify(emailVerificationRepository).save(testVerification);
        assertTrue(testVerification.isUsed());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifyCode_ShouldThrowExceptionWhenNoVerificationFound() {
        // Arrange
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(Exception.class, () -> emailVerificationService.verifyCode("test@example.com", "123456"));
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resendVerificationCode_ShouldCreateNewVerificationForUnverifiedUser() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(emailVerificationRepository.countByEmailAndCreatedAtAfterAndUsedFalse(anyString(), any(LocalDateTime.class)))
            .thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedCode");
        when(emailVerificationRepository.save(any(EmailVerification.class))).thenReturn(testVerification);

        // Act
        emailVerificationService.resendVerificationCode("test@example.com");

        // Assert
        verify(emailVerificationRepository).markAllAsUsedByEmail("test@example.com");
        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), anyString());
    }

    @Test
    void resendVerificationCode_ShouldThrowExceptionForVerifiedUser() {
        // Arrange
        testUser.setEmailVerified(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> emailVerificationService.resendVerificationCode("test@example.com"));
        
        assertTrue(exception.getMessage().contains("Email is already verified"));
        verify(emailVerificationRepository, never()).save(any(EmailVerification.class));
    }

    @Test
    void resendVerificationCode_ShouldThrowExceptionForNonExistentUser() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> emailVerificationService.resendVerificationCode("test@example.com"));
        
        assertTrue(exception.getMessage().contains("User not found"));
        verify(emailVerificationRepository, never()).save(any(EmailVerification.class));
    }

    @Test
    void getRemainingCodeRequests_ShouldReturnCorrectCount() {
        // Arrange
        when(emailVerificationRepository.countByEmailAndCreatedAtAfterAndUsedFalse(anyString(), any(LocalDateTime.class)))
            .thenReturn(2L);

        // Act
        int remaining = emailVerificationService.getRemainingCodeRequests("test@example.com");

        // Assert
        assertEquals(3, remaining); // 5 max - 2 used = 3 remaining
    }

    @Test
    void hasValidVerificationCode_ShouldReturnTrueForValidCode() {
        // Arrange
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.of(testVerification));

        // Act
        boolean hasValid = emailVerificationService.hasValidVerificationCode("test@example.com");

        // Assert
        assertTrue(hasValid);
    }

    @Test
    void hasValidVerificationCode_ShouldReturnFalseForExpiredCode() {
        // Arrange
        testVerification.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.of(testVerification));

        // Act
        boolean hasValid = emailVerificationService.hasValidVerificationCode("test@example.com");

        // Assert
        assertFalse(hasValid);
    }

    @Test
    void getVerificationCodeExpiry_ShouldReturnExpiryForValidCode() {
        // Arrange
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);
        testVerification.setExpiresAt(expiry);
        when(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(Optional.of(testVerification));

        // Act
        Optional<LocalDateTime> result = emailVerificationService.getVerificationCodeExpiry("test@example.com");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expiry, result.get());
    }

    @Test
    void cleanupExpiredVerifications_ShouldMarkExpiredCodesAsUsed() {
        // Arrange
        EmailVerification expiredVerification = new EmailVerification();
        expiredVerification.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        expiredVerification.setUsed(false);
        
        List<EmailVerification> expiredList = Arrays.asList(expiredVerification);
        when(emailVerificationRepository.findByExpiresAtBeforeAndUsedFalse(any(LocalDateTime.class)))
            .thenReturn(expiredList);
        when(emailVerificationRepository.saveAll(anyList())).thenReturn(expiredList);

        // Act
        emailVerificationService.cleanupExpiredVerifications();

        // Assert
        verify(emailVerificationRepository).saveAll(expiredList);
        verify(emailVerificationRepository).deleteExpiredVerifications(any(LocalDateTime.class));
        assertTrue(expiredVerification.isUsed());
    }

    @Test
    void getVerificationStats_ShouldReturnCorrectStats() {
        // Arrange
        when(emailVerificationRepository.count()).thenReturn(10L);
        when(emailVerificationRepository.findByExpiresAtBeforeAndUsedFalse(any(LocalDateTime.class)))
            .thenReturn(Arrays.asList(new EmailVerification(), new EmailVerification()));

        // Act
        EmailVerificationService.VerificationStats stats = emailVerificationService.getVerificationStats();

        // Assert
        assertEquals(10L, stats.getTotalVerifications());
        assertEquals(2L, stats.getExpiredCodes());
    }
}