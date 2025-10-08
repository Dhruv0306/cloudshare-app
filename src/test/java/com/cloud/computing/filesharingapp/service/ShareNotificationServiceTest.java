package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareNotification;
import com.cloud.computing.filesharingapp.entity.SharePermission;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.ShareNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareNotificationServiceTest {

    @Mock
    private ShareNotificationRepository shareNotificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private ShareNotificationService shareNotificationService;

    private User testUser;
    private FileEntity testFile;
    private FileShare testFileShare;
    private ShareNotification testNotification;

    @BeforeEach
    void setUp() {
        // Set up test configuration values
        ReflectionTestUtils.setField(shareNotificationService, "fromEmail", "test@example.com");
        ReflectionTestUtils.setField(shareNotificationService, "fromName", "Test App");
        ReflectionTestUtils.setField(shareNotificationService, "baseUrl", "http://localhost:8080");

        // Create test entities
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("testuser@example.com");

        testFile = new FileEntity();
        testFile.setId(1L);
        testFile.setFileName("test-document.pdf");
        testFile.setFileSize(1024L);
        testFile.setOwner(testUser);

        testFileShare = new FileShare();
        testFileShare.setId(1L);
        testFileShare.setFile(testFile);
        testFileShare.setOwner(testUser);
        testFileShare.setShareToken("test-token-123");
        testFileShare.setPermission(SharePermission.DOWNLOAD);
        testFileShare.setCreatedAt(LocalDateTime.now());
        testFileShare.setExpiresAt(LocalDateTime.now().plusDays(7));
        testFileShare.setActive(true);

        testNotification = new ShareNotification();
        testNotification.setId(1L);
        testNotification.setFileShare(testFileShare);
        testNotification.setRecipientEmail("recipient@example.com");
        testNotification.setNotificationId("notification-123");
        testNotification.setSentAt(LocalDateTime.now());
    }

    @Test
    void testSendShareNotifications_Success() {
        // Arrange
        List<String> recipients = Arrays.asList("user1@example.com", "user2@example.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);

        // Act
        shareNotificationService.sendShareNotifications(testFileShare, recipients);

        // Assert
        verify(shareNotificationRepository, times(4)).save(any(ShareNotification.class)); // 2 recipients * 2 saves each (create + mark delivered)
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void testSendShareNotifications_NullFileShare() {
        // Arrange
        List<String> recipients = Arrays.asList("user1@example.com");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            shareNotificationService.sendShareNotifications(null, recipients);
        });
    }

    @Test
    void testSendShareNotifications_EmptyRecipients() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            shareNotificationService.sendShareNotifications(testFileShare, Collections.emptyList());
        });
    }

    @Test
    void testSendSingleShareNotification_Success() {
        // Arrange
        String recipientEmail = "recipient@example.com";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);

        // Act
        shareNotificationService.sendSingleShareNotification(testFileShare, recipientEmail);

        // Assert
        verify(shareNotificationRepository, times(2)).save(any(ShareNotification.class)); // Once for creation, once for marking delivered
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendSingleShareNotification_InvalidEmail() {
        // Arrange
        String invalidEmail = "invalid-email";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            shareNotificationService.sendSingleShareNotification(testFileShare, invalidEmail);
        });
    }

    @Test
    void testSendSingleShareNotification_EmailFailure() {
        // Arrange
        String recipientEmail = "recipient@example.com";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);
        doThrow(new MailException("Email sending failed") {}).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            shareNotificationService.sendSingleShareNotification(testFileShare, recipientEmail);
        });

        // Verify notification was saved but not marked as delivered
        verify(shareNotificationRepository, times(1)).save(any(ShareNotification.class));
    }

    @Test
    void testSendShareRevokedNotifications_Success() {
        // Arrange
        List<String> recipients = Arrays.asList("user1@example.com", "user2@example.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        shareNotificationService.sendShareRevokedNotifications(testFileShare, recipients);

        // Assert
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void testGetNotificationHistory() {
        // Arrange
        List<ShareNotification> expectedNotifications = Arrays.asList(testNotification);
        when(shareNotificationRepository.findByFileShareOrderBySentAtDesc(testFileShare))
                .thenReturn(expectedNotifications);

        // Act
        List<ShareNotification> result = shareNotificationService.getNotificationHistory(testFileShare);

        // Assert
        assertEquals(expectedNotifications, result);
        verify(shareNotificationRepository).findByFileShareOrderBySentAtDesc(testFileShare);
    }

    @Test
    void testGetNotificationStats() {
        // Arrange
        when(shareNotificationRepository.countByFileShare(testFileShare)).thenReturn(10L);
        when(shareNotificationRepository.countByFileShareAndDeliveredTrue(testFileShare)).thenReturn(8L);
        when(shareNotificationRepository.countByFileShareAndDeliveredFalse(testFileShare)).thenReturn(2L);

        // Act
        ShareNotificationService.ShareNotificationStats stats = 
                shareNotificationService.getNotificationStats(testFileShare);

        // Assert
        assertEquals(10L, stats.getTotalNotifications());
        assertEquals(8L, stats.getDeliveredNotifications());
        assertEquals(2L, stats.getFailedNotifications());
        assertEquals(80.0, stats.getDeliveryRate(), 0.1);
    }

    @Test
    void testRetryFailedNotifications_Success() {
        // Arrange
        List<ShareNotification> failedNotifications = Arrays.asList(testNotification);
        when(shareNotificationRepository.findNotificationsForRetry(any(LocalDateTime.class)))
                .thenReturn(failedNotifications);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);

        // Act
        int result = shareNotificationService.retryFailedNotifications(24);

        // Assert
        assertEquals(1, result);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(shareNotificationRepository, times(1)).save(any(ShareNotification.class));
    }

    @Test
    void testRetryFailedNotifications_NoFailedNotifications() {
        // Arrange
        when(shareNotificationRepository.findNotificationsForRetry(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        int result = shareNotificationService.retryFailedNotifications(24);

        // Assert
        assertEquals(0, result);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testIsValidEmail() {
        // Test valid emails
        assertTrue(shareNotificationService.isValidEmail("user@example.com"));
        assertTrue(shareNotificationService.isValidEmail("test.email+tag@domain.co.uk"));
        assertTrue(shareNotificationService.isValidEmail("user123@test-domain.org"));

        // Test invalid emails
        assertFalse(shareNotificationService.isValidEmail("invalid-email"));
        assertFalse(shareNotificationService.isValidEmail("@example.com"));
        assertFalse(shareNotificationService.isValidEmail("user@"));
        assertFalse(shareNotificationService.isValidEmail(""));
        assertFalse(shareNotificationService.isValidEmail(null));
        assertFalse(shareNotificationService.isValidEmail("user@.com"));
        assertFalse(shareNotificationService.isValidEmail("user..name@example.com"));
    }

    @Test
    void testSendSingleShareNotification_RuntimeException() throws Exception {
        // Arrange
        String recipientEmail = "recipient@example.com";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);
        doThrow(new RuntimeException("Unexpected error")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            shareNotificationService.sendSingleShareNotification(testFileShare, recipientEmail);
        });

        // Verify notification was saved but not marked as delivered due to exception
        verify(shareNotificationRepository, times(1)).save(any(ShareNotification.class));
    }

    @Test
    void testSendShareNotifications_WithInvalidEmails() {
        // Arrange
        List<String> recipients = Arrays.asList("valid@example.com", "invalid-email", "another@valid.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);

        // Act
        shareNotificationService.sendShareNotifications(testFileShare, recipients);

        // Assert - should only send to valid emails (2 out of 3)
        verify(shareNotificationRepository, times(4)).save(any(ShareNotification.class)); // 2 valid recipients * 2 saves each
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void testSendShareRevokedNotifications_NullFileShare() {
        // Arrange
        List<String> recipients = Arrays.asList("user1@example.com");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            shareNotificationService.sendShareRevokedNotifications(null, recipients);
        });
    }

    @Test
    void testSendShareRevokedNotifications_EmptyRecipients() {
        // Arrange & Act - should not throw exception, just return silently
        assertDoesNotThrow(() -> {
            shareNotificationService.sendShareRevokedNotifications(testFileShare, Collections.emptyList());
        });

        // Assert - no emails should be sent
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testSendShareRevokedNotifications_NullRecipients() {
        // Arrange & Act - should not throw exception, just return silently
        assertDoesNotThrow(() -> {
            shareNotificationService.sendShareRevokedNotifications(testFileShare, null);
        });

        // Assert - no emails should be sent
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testSendShareRevokedNotifications_WithInvalidEmails() {
        // Arrange
        List<String> recipients = Arrays.asList("valid@example.com", "invalid-email", "another@valid.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        shareNotificationService.sendShareRevokedNotifications(testFileShare, recipients);

        // Assert - should only send to valid emails (2 out of 3)
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void testSendShareRevokedNotifications_MailException() throws Exception {
        // Arrange
        List<String> recipients = Arrays.asList("user1@example.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailException("Mail sending failed") {}).when(mailSender).send(any(MimeMessage.class));

        // Act - should not throw exception, just log error
        assertDoesNotThrow(() -> {
            shareNotificationService.sendShareRevokedNotifications(testFileShare, recipients);
        });

        // Assert - attempt was made to send email
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testRetryFailedNotifications_WithMailException() throws Exception {
        // Arrange
        List<ShareNotification> failedNotifications = Arrays.asList(testNotification);
        when(shareNotificationRepository.findNotificationsForRetry(any(LocalDateTime.class)))
                .thenReturn(failedNotifications);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailException("Retry failed") {}).when(mailSender).send(any(MimeMessage.class));

        // Act
        int result = shareNotificationService.retryFailedNotifications(24);

        // Assert - should return 0 successful retries due to exception
        assertEquals(0, result);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(shareNotificationRepository, never()).save(any(ShareNotification.class)); // No save due to failure
    }

    @Test
    void testIsValidEmail_EdgeCases() {
        // Test additional edge cases for email validation
        assertTrue(shareNotificationService.isValidEmail("a@b.co"));
        assertTrue(shareNotificationService.isValidEmail("test123@example-domain.com"));
        assertTrue(shareNotificationService.isValidEmail("user.name+tag@example.org"));
        assertTrue(shareNotificationService.isValidEmail("user_name@example.net"));
        
        // Test invalid cases
        assertFalse(shareNotificationService.isValidEmail("user@domain"));
        assertFalse(shareNotificationService.isValidEmail("user@domain."));
        assertFalse(shareNotificationService.isValidEmail("user@.domain.com"));
        assertFalse(shareNotificationService.isValidEmail("user name@example.com")); // space in local part
        assertFalse(shareNotificationService.isValidEmail("user@domain..com")); // double dot
        assertFalse(shareNotificationService.isValidEmail("   ")); // whitespace only
        assertFalse(shareNotificationService.isValidEmail("user@domain.c")); // TLD too short
    }

    @Test
    void testEmailTemplateGeneration_ViewOnlyPermission() {
        // Arrange
        testFileShare.setPermission(SharePermission.VIEW_ONLY);
        testFileShare.setExpiresAt(null); // Test no expiration case
        String recipientEmail = "recipient@example.com";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);

        // Act
        shareNotificationService.sendSingleShareNotification(testFileShare, recipientEmail);

        // Assert
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(shareNotificationRepository, times(2)).save(any(ShareNotification.class));
    }

    @Test
    void testEmailTemplateGeneration_LargeFileSize() {
        // Arrange
        testFile.setFileSize(1073741824L); // 1GB file
        String recipientEmail = "recipient@example.com";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(shareNotificationRepository.save(any(ShareNotification.class))).thenReturn(testNotification);

        // Act
        shareNotificationService.sendSingleShareNotification(testFileShare, recipientEmail);

        // Assert
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(shareNotificationRepository, times(2)).save(any(ShareNotification.class));
    }

    @Test
    void testGetNotificationStats_NullFileShare() {
        // Arrange
        when(shareNotificationRepository.countByFileShare(null)).thenReturn(0L);
        when(shareNotificationRepository.countByFileShareAndDeliveredTrue(null)).thenReturn(0L);
        when(shareNotificationRepository.countByFileShareAndDeliveredFalse(null)).thenReturn(0L);

        // Act
        ShareNotificationService.ShareNotificationStats stats = 
                shareNotificationService.getNotificationStats(null);

        // Assert
        assertEquals(0L, stats.getTotalNotifications());
        assertEquals(0L, stats.getDeliveredNotifications());
        assertEquals(0L, stats.getFailedNotifications());
    }

    @Test
    void testGetNotificationHistory_NullFileShare() {
        // Arrange
        when(shareNotificationRepository.findByFileShareOrderBySentAtDesc(null))
                .thenReturn(Collections.emptyList());

        // Act
        List<ShareNotification> result = shareNotificationService.getNotificationHistory(null);

        // Assert
        assertTrue(result.isEmpty());
        verify(shareNotificationRepository).findByFileShareOrderBySentAtDesc(null);
    }

    @Test
    void testRetryFailedNotifications_ZeroMaxRetryHours() {
        // Arrange
        when(shareNotificationRepository.findNotificationsForRetry(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        int result = shareNotificationService.retryFailedNotifications(0);

        // Assert
        assertEquals(0, result);
        verify(shareNotificationRepository).findNotificationsForRetry(any(LocalDateTime.class));
    }

    @Test
    void testRetryFailedNotifications_NegativeMaxRetryHours() {
        // Arrange
        when(shareNotificationRepository.findNotificationsForRetry(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        int result = shareNotificationService.retryFailedNotifications(-1);

        // Assert
        assertEquals(0, result);
        verify(shareNotificationRepository).findNotificationsForRetry(any(LocalDateTime.class));
    }

    @Test
    void testShareNotificationStats() {
        // Test ShareNotificationStats class
        ShareNotificationService.ShareNotificationStats stats = 
                new ShareNotificationService.ShareNotificationStats(100L, 85L, 15L);

        assertEquals(100L, stats.getTotalNotifications());
        assertEquals(85L, stats.getDeliveredNotifications());
        assertEquals(15L, stats.getFailedNotifications());
        assertEquals(85.0, stats.getDeliveryRate(), 0.1);

        // Test zero total notifications
        ShareNotificationService.ShareNotificationStats emptyStats = 
                new ShareNotificationService.ShareNotificationStats(0L, 0L, 0L);
        assertEquals(0.0, emptyStats.getDeliveryRate(), 0.1);

        // Test toString method
        String statsString = stats.toString();
        assertTrue(statsString.contains("total=100"));
        assertTrue(statsString.contains("delivered=85"));
        assertTrue(statsString.contains("failed=15"));
        assertTrue(statsString.contains("deliveryRate=85.0%"));
    }

    @Test
    void testShareNotificationStats_EdgeCases() {
        // Test with all failed notifications
        ShareNotificationService.ShareNotificationStats allFailedStats = 
                new ShareNotificationService.ShareNotificationStats(10L, 0L, 10L);
        assertEquals(0.0, allFailedStats.getDeliveryRate(), 0.1);

        // Test with all successful notifications
        ShareNotificationService.ShareNotificationStats allSuccessStats = 
                new ShareNotificationService.ShareNotificationStats(10L, 10L, 0L);
        assertEquals(100.0, allSuccessStats.getDeliveryRate(), 0.1);

        // Test with single notification
        ShareNotificationService.ShareNotificationStats singleStats = 
                new ShareNotificationService.ShareNotificationStats(1L, 1L, 0L);
        assertEquals(100.0, singleStats.getDeliveryRate(), 0.1);
    }
}