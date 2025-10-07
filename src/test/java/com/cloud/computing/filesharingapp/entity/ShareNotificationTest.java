package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unit tests for the ShareNotification entity.
 * 
 * <p>Tests verify entity properties, relationships, business methods,
 * and initialization for email notification tracking.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class ShareNotificationTest {

    private ShareNotification shareNotification;
    private FileShare fileShare;
    private FileEntity file;
    private User owner;
    private String notificationId;

    @BeforeEach
    void setUp() {
        owner = new User("testuser", "test@example.com", "password123");
        file = new FileEntity("test_file.txt", "file.txt", "text/plain", 1024L, "/uploads/test_file.txt", owner);
        fileShare = new FileShare(file, owner, UUID.randomUUID().toString(), SharePermission.DOWNLOAD);
        notificationId = UUID.randomUUID().toString();
        shareNotification = new ShareNotification();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(shareNotification);
        assertNotNull(shareNotification.getSentAt());
        assertFalse(shareNotification.isDelivered());
        
        LocalDateTime now = LocalDateTime.now();
        assertTrue(shareNotification.getSentAt().isBefore(now.plusSeconds(1)), 
                "Sent timestamp should be close to current time");
        assertTrue(shareNotification.getSentAt().isAfter(now.minusSeconds(1)), 
                "Sent timestamp should be close to current time");
    }

    @Test
    void testParameterizedConstructor() {
        String recipientEmail = "recipient@example.com";
        
        ShareNotification notification = new ShareNotification(fileShare, recipientEmail, notificationId);
        
        assertEquals(fileShare, notification.getFileShare());
        assertEquals(recipientEmail, notification.getRecipientEmail());
        assertEquals(notificationId, notification.getNotificationId());
        assertNotNull(notification.getSentAt());
        assertFalse(notification.isDelivered());
    }

    @Test
    void testSettersAndGetters() {
        Long id = 1L;
        String recipientEmail = "test@example.com";
        LocalDateTime sentAt = LocalDateTime.now().minusHours(2);
        boolean delivered = true;
        
        shareNotification.setId(id);
        shareNotification.setFileShare(fileShare);
        shareNotification.setRecipientEmail(recipientEmail);
        shareNotification.setSentAt(sentAt);
        shareNotification.setDelivered(delivered);
        shareNotification.setNotificationId(notificationId);
        
        assertEquals(id, shareNotification.getId());
        assertEquals(fileShare, shareNotification.getFileShare());
        assertEquals(recipientEmail, shareNotification.getRecipientEmail());
        assertEquals(sentAt, shareNotification.getSentAt());
        assertEquals(delivered, shareNotification.isDelivered());
        assertEquals(notificationId, shareNotification.getNotificationId());
    }

    @Test
    void testMarkAsDelivered() {
        assertFalse(shareNotification.isDelivered(), "Notification should not be delivered initially");
        
        shareNotification.markAsDelivered();
        
        assertTrue(shareNotification.isDelivered(), "Notification should be marked as delivered");
    }

    @Test
    void testMarkAsDeliveredMultipleTimes() {
        shareNotification.markAsDelivered();
        assertTrue(shareNotification.isDelivered());
        
        // Calling again should not change the state
        shareNotification.markAsDelivered();
        assertTrue(shareNotification.isDelivered());
    }

    @Test
    void testFileShareRelationship() {
        shareNotification.setFileShare(fileShare);
        
        assertEquals(fileShare, shareNotification.getFileShare());
        assertEquals(file, shareNotification.getFileShare().getFile());
        assertEquals(owner, shareNotification.getFileShare().getOwner());
    }

    @Test
    void testRecipientEmailValidation() {
        // Test valid email addresses
        String[] validEmails = {
            "user@example.com",
            "test.email@domain.co.uk",
            "user+tag@example.org",
            "firstname.lastname@company.com"
        };
        
        for (String email : validEmails) {
            shareNotification.setRecipientEmail(email);
            assertEquals(email, shareNotification.getRecipientEmail());
        }
    }

    @Test
    void testNotificationIdValidation() {
        String validUuid = UUID.randomUUID().toString();
        shareNotification.setNotificationId(validUuid);
        
        assertEquals(validUuid, shareNotification.getNotificationId());
        assertEquals(36, shareNotification.getNotificationId().length(), 
                "UUID should be 36 characters long");
    }

    @Test
    void testSentAtInitialization() {
        ShareNotification newNotification = new ShareNotification();
        LocalDateTime now = LocalDateTime.now();
        
        assertNotNull(newNotification.getSentAt());
        assertTrue(newNotification.getSentAt().isBefore(now.plusSeconds(1)), 
                "Sent timestamp should be initialized to current time");
        assertTrue(newNotification.getSentAt().isAfter(now.minusSeconds(1)), 
                "Sent timestamp should be initialized to current time");
    }

    @Test
    void testSentAtCustomValue() {
        LocalDateTime customTime = LocalDateTime.of(2023, 6, 15, 10, 30, 0);
        shareNotification.setSentAt(customTime);
        
        assertEquals(customTime, shareNotification.getSentAt());
    }

    @Test
    void testDefaultDeliveredStatus() {
        ShareNotification newNotification = new ShareNotification();
        
        assertFalse(newNotification.isDelivered(), "New notifications should not be delivered by default");
    }

    @Test
    void testDeliveredStatusToggle() {
        shareNotification.setDelivered(true);
        assertTrue(shareNotification.isDelivered());
        
        shareNotification.setDelivered(false);
        assertFalse(shareNotification.isDelivered());
    }

    @Test
    void testNullFileShare() {
        shareNotification.setFileShare(null);
        assertNull(shareNotification.getFileShare());
    }

    @Test
    void testNullRecipientEmail() {
        shareNotification.setRecipientEmail(null);
        assertNull(shareNotification.getRecipientEmail());
    }

    @Test
    void testNullNotificationId() {
        shareNotification.setNotificationId(null);
        assertNull(shareNotification.getNotificationId());
    }

    @Test
    void testEmptyRecipientEmail() {
        shareNotification.setRecipientEmail("");
        assertEquals("", shareNotification.getRecipientEmail());
    }

    @Test
    void testCompleteNotificationScenario() {
        // Test a complete notification scenario
        String recipientEmail = "recipient@company.com";
        String notificationUuid = UUID.randomUUID().toString();
        
        ShareNotification completeNotification = new ShareNotification(fileShare, recipientEmail, notificationUuid);
        completeNotification.setId(456L);
        
        assertEquals(456L, completeNotification.getId());
        assertEquals(fileShare, completeNotification.getFileShare());
        assertEquals(recipientEmail, completeNotification.getRecipientEmail());
        assertEquals(notificationUuid, completeNotification.getNotificationId());
        assertNotNull(completeNotification.getSentAt());
        assertFalse(completeNotification.isDelivered());
        
        // Mark as delivered
        completeNotification.markAsDelivered();
        assertTrue(completeNotification.isDelivered());
    }

    @Test
    void testNotificationWithMinimalData() {
        // Test notification with minimal required data
        ShareNotification minimalNotification = new ShareNotification();
        minimalNotification.setFileShare(fileShare);
        minimalNotification.setRecipientEmail("minimal@example.com");
        
        assertEquals(fileShare, minimalNotification.getFileShare());
        assertEquals("minimal@example.com", minimalNotification.getRecipientEmail());
        assertNotNull(minimalNotification.getSentAt());
        assertFalse(minimalNotification.isDelivered());
        assertNull(minimalNotification.getNotificationId());
    }

    @Test
    void testLongRecipientEmail() {
        // Test with a long but valid email address
        String longEmail = "very.long.email.address.with.multiple.dots@very-long-domain-name.example.com";
        shareNotification.setRecipientEmail(longEmail);
        
        assertEquals(longEmail, shareNotification.getRecipientEmail());
    }

    @Test
    void testNotificationDeliveryWorkflow() {
        // Test typical notification delivery workflow
        String recipientEmail = "workflow@example.com";
        ShareNotification notification = new ShareNotification(fileShare, recipientEmail, notificationId);
        
        // Initially not delivered
        assertFalse(notification.isDelivered());
        
        // Simulate successful delivery
        notification.markAsDelivered();
        assertTrue(notification.isDelivered());
        
        // Verify all properties are maintained
        assertEquals(fileShare, notification.getFileShare());
        assertEquals(recipientEmail, notification.getRecipientEmail());
        assertEquals(notificationId, notification.getNotificationId());
        assertNotNull(notification.getSentAt());
    }
}