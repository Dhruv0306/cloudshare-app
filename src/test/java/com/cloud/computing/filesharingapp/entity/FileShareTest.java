package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Unit tests for the FileShare entity.
 * 
 * <p>Tests verify entity properties, relationships, validation logic,
 * and business methods for file sharing functionality.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class FileShareTest {

    private FileShare fileShare;
    private FileEntity file;
    private User owner;
    private String shareToken;

    @BeforeEach
    void setUp() {
        owner = new User("testuser", "test@example.com", "password123");
        file = new FileEntity("test_file.txt", "file.txt", "text/plain", 1024L, "/uploads/test_file.txt", owner);
        shareToken = UUID.randomUUID().toString();
        fileShare = new FileShare();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(fileShare);
        assertNotNull(fileShare.getCreatedAt());
        assertTrue(fileShare.isActive());
        assertEquals(0, fileShare.getAccessCount());
    }

    @Test
    void testParameterizedConstructor() {
        FileShare share = new FileShare(file, owner, shareToken, SharePermission.DOWNLOAD);
        
        assertEquals(file, share.getFile());
        assertEquals(owner, share.getOwner());
        assertEquals(shareToken, share.getShareToken());
        assertEquals(SharePermission.DOWNLOAD, share.getPermission());
        assertNotNull(share.getCreatedAt());
        assertTrue(share.isActive());
        assertEquals(0, share.getAccessCount());
    }

    @Test
    void testSettersAndGetters() {
        Long id = 1L;
        LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        Integer maxAccess = 10;
        
        fileShare.setId(id);
        fileShare.setFile(file);
        fileShare.setOwner(owner);
        fileShare.setShareToken(shareToken);
        fileShare.setPermission(SharePermission.VIEW_ONLY);
        fileShare.setCreatedAt(createdAt);
        fileShare.setExpiresAt(expiresAt);
        fileShare.setActive(false);
        fileShare.setAccessCount(5);
        fileShare.setMaxAccess(maxAccess);
        
        assertEquals(id, fileShare.getId());
        assertEquals(file, fileShare.getFile());
        assertEquals(owner, fileShare.getOwner());
        assertEquals(shareToken, fileShare.getShareToken());
        assertEquals(SharePermission.VIEW_ONLY, fileShare.getPermission());
        assertEquals(createdAt, fileShare.getCreatedAt());
        assertEquals(expiresAt, fileShare.getExpiresAt());
        assertFalse(fileShare.isActive());
        assertEquals(5, fileShare.getAccessCount());
        assertEquals(maxAccess, fileShare.getMaxAccess());
    }

    @Test
    void testIsValidWhenActive() {
        fileShare.setActive(true);
        fileShare.setExpiresAt(null);
        fileShare.setMaxAccess(null);
        
        assertTrue(fileShare.isValid(), "Active share with no expiration should be valid");
    }

    @Test
    void testIsValidWhenInactive() {
        fileShare.setActive(false);
        
        assertFalse(fileShare.isValid(), "Inactive share should not be valid");
    }

    @Test
    void testIsValidWhenExpired() {
        fileShare.setActive(true);
        fileShare.setExpiresAt(LocalDateTime.now().minusHours(1));
        
        assertFalse(fileShare.isValid(), "Expired share should not be valid");
    }

    @Test
    void testIsValidWhenNotExpired() {
        fileShare.setActive(true);
        fileShare.setExpiresAt(LocalDateTime.now().plusHours(1));
        
        assertTrue(fileShare.isValid(), "Non-expired share should be valid");
    }

    @Test
    void testIsValidWhenMaxAccessReached() {
        fileShare.setActive(true);
        fileShare.setMaxAccess(5);
        fileShare.setAccessCount(5);
        
        assertFalse(fileShare.isValid(), "Share with max access reached should not be valid");
    }

    @Test
    void testIsValidWhenMaxAccessNotReached() {
        fileShare.setActive(true);
        fileShare.setMaxAccess(5);
        fileShare.setAccessCount(3);
        
        assertTrue(fileShare.isValid(), "Share with access count below max should be valid");
    }

    @Test
    void testIsValidWhenMaxAccessExceeded() {
        fileShare.setActive(true);
        fileShare.setMaxAccess(5);
        fileShare.setAccessCount(6);
        
        assertFalse(fileShare.isValid(), "Share with access count exceeding max should not be valid");
    }

    @Test
    void testIncrementAccessCount() {
        int initialCount = fileShare.getAccessCount();
        
        fileShare.incrementAccessCount();
        
        assertEquals(initialCount + 1, fileShare.getAccessCount(), 
                "Access count should be incremented by 1");
    }

    @Test
    void testIncrementAccessCountMultipleTimes() {
        int initialCount = fileShare.getAccessCount();
        
        fileShare.incrementAccessCount();
        fileShare.incrementAccessCount();
        fileShare.incrementAccessCount();
        
        assertEquals(initialCount + 3, fileShare.getAccessCount(), 
                "Access count should be incremented correctly multiple times");
    }

    @Test
    void testFileRelationship() {
        fileShare.setFile(file);
        
        assertEquals(file, fileShare.getFile());
        assertEquals("test_file.txt", fileShare.getFile().getFileName());
    }

    @Test
    void testOwnerRelationship() {
        fileShare.setOwner(owner);
        
        assertEquals(owner, fileShare.getOwner());
        assertEquals("testuser", fileShare.getOwner().getUsername());
    }

    @Test
    void testAccessLogsRelationship() {
        ArrayList<ShareAccess> accessLogs = new ArrayList<>();
        fileShare.setAccessLogs(accessLogs);
        
        assertEquals(accessLogs, fileShare.getAccessLogs());
        assertNotNull(fileShare.getAccessLogs());
    }

    @Test
    void testNotificationsRelationship() {
        ArrayList<ShareNotification> notifications = new ArrayList<>();
        fileShare.setNotifications(notifications);
        
        assertEquals(notifications, fileShare.getNotifications());
        assertNotNull(fileShare.getNotifications());
    }

    @Test
    void testShareTokenValidation() {
        String validToken = UUID.randomUUID().toString();
        fileShare.setShareToken(validToken);
        
        assertEquals(validToken, fileShare.getShareToken());
        assertEquals(36, fileShare.getShareToken().length(), 
                "UUID token should be 36 characters long");
    }

    @Test
    void testPermissionEnumValues() {
        fileShare.setPermission(SharePermission.VIEW_ONLY);
        assertEquals(SharePermission.VIEW_ONLY, fileShare.getPermission());
        
        fileShare.setPermission(SharePermission.DOWNLOAD);
        assertEquals(SharePermission.DOWNLOAD, fileShare.getPermission());
    }

    @Test
    void testCreatedAtInitialization() {
        FileShare newShare = new FileShare();
        LocalDateTime now = LocalDateTime.now();
        
        assertNotNull(newShare.getCreatedAt());
        assertTrue(newShare.getCreatedAt().isBefore(now.plusSeconds(1)), 
                "Created timestamp should be close to current time");
        assertTrue(newShare.getCreatedAt().isAfter(now.minusSeconds(1)), 
                "Created timestamp should be close to current time");
    }

    @Test
    void testDefaultActiveStatus() {
        FileShare newShare = new FileShare();
        
        assertTrue(newShare.isActive(), "New shares should be active by default");
    }

    @Test
    void testDefaultAccessCount() {
        FileShare newShare = new FileShare();
        
        assertEquals(0, newShare.getAccessCount(), "New shares should have zero access count");
    }

    @Test
    void testComplexValidityScenario() {
        // Test a share that is active, not expired, but has reached max access
        fileShare.setActive(true);
        fileShare.setExpiresAt(LocalDateTime.now().plusDays(1));
        fileShare.setMaxAccess(3);
        fileShare.setAccessCount(3);
        
        assertFalse(fileShare.isValid(), 
                "Share should be invalid when max access is reached even if active and not expired");
    }

    @Test
    void testAnotherComplexValidityScenario() {
        // Test a share that is active, has access remaining, but is expired
        fileShare.setActive(true);
        fileShare.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        fileShare.setMaxAccess(10);
        fileShare.setAccessCount(2);
        
        assertFalse(fileShare.isValid(), 
                "Share should be invalid when expired even if active and has access remaining");
    }
}