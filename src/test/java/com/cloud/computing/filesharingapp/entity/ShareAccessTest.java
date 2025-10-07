package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unit tests for the ShareAccess entity.
 * 
 * <p>Tests verify entity properties, relationships, and initialization
 * for tracking file share access logs.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class ShareAccessTest {

    private ShareAccess shareAccess;
    private FileShare fileShare;
    private FileEntity file;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User("testuser", "test@example.com", "password123");
        file = new FileEntity("test_file.txt", "file.txt", "text/plain", 1024L, "/uploads/test_file.txt", owner);
        fileShare = new FileShare(file, owner, UUID.randomUUID().toString(), SharePermission.DOWNLOAD);
        shareAccess = new ShareAccess();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(shareAccess);
        assertNotNull(shareAccess.getAccessedAt());
        
        LocalDateTime now = LocalDateTime.now();
        assertTrue(shareAccess.getAccessedAt().isBefore(now.plusSeconds(1)), 
                "Access timestamp should be close to current time");
        assertTrue(shareAccess.getAccessedAt().isAfter(now.minusSeconds(1)), 
                "Access timestamp should be close to current time");
    }

    @Test
    void testParameterizedConstructor() {
        String accessorIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;
        
        ShareAccess access = new ShareAccess(fileShare, accessorIp, userAgent, accessType);
        
        assertEquals(fileShare, access.getFileShare());
        assertEquals(accessorIp, access.getAccessorIp());
        assertEquals(userAgent, access.getUserAgent());
        assertEquals(accessType, access.getAccessType());
        assertNotNull(access.getAccessedAt());
    }

    @Test
    void testSettersAndGetters() {
        Long id = 1L;
        String accessorIp = "10.0.0.1";
        String userAgent = "Chrome/91.0.4472.124";
        LocalDateTime accessedAt = LocalDateTime.now().minusMinutes(30);
        ShareAccessType accessType = ShareAccessType.VIEW;
        
        shareAccess.setId(id);
        shareAccess.setFileShare(fileShare);
        shareAccess.setAccessorIp(accessorIp);
        shareAccess.setUserAgent(userAgent);
        shareAccess.setAccessedAt(accessedAt);
        shareAccess.setAccessType(accessType);
        
        assertEquals(id, shareAccess.getId());
        assertEquals(fileShare, shareAccess.getFileShare());
        assertEquals(accessorIp, shareAccess.getAccessorIp());
        assertEquals(userAgent, shareAccess.getUserAgent());
        assertEquals(accessedAt, shareAccess.getAccessedAt());
        assertEquals(accessType, shareAccess.getAccessType());
    }

    @Test
    void testFileShareRelationship() {
        shareAccess.setFileShare(fileShare);
        
        assertEquals(fileShare, shareAccess.getFileShare());
        assertEquals(file, shareAccess.getFileShare().getFile());
        assertEquals(owner, shareAccess.getFileShare().getOwner());
    }

    @Test
    void testAccessorIpValidation() {
        // Test IPv4 address
        String ipv4 = "192.168.1.1";
        shareAccess.setAccessorIp(ipv4);
        assertEquals(ipv4, shareAccess.getAccessorIp());
        
        // Test IPv6 address
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        shareAccess.setAccessorIp(ipv6);
        assertEquals(ipv6, shareAccess.getAccessorIp());
        
        // Test localhost
        String localhost = "127.0.0.1";
        shareAccess.setAccessorIp(localhost);
        assertEquals(localhost, shareAccess.getAccessorIp());
    }

    @Test
    void testUserAgentHandling() {
        // Test common user agent strings
        String chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
        shareAccess.setUserAgent(chromeUserAgent);
        assertEquals(chromeUserAgent, shareAccess.getUserAgent());
        
        String firefoxUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0";
        shareAccess.setUserAgent(firefoxUserAgent);
        assertEquals(firefoxUserAgent, shareAccess.getUserAgent());
        
        // Test null user agent
        shareAccess.setUserAgent(null);
        assertNull(shareAccess.getUserAgent());
        
        // Test empty user agent
        shareAccess.setUserAgent("");
        assertEquals("", shareAccess.getUserAgent());
    }

    @Test
    void testAccessTypeEnumValues() {
        shareAccess.setAccessType(ShareAccessType.VIEW);
        assertEquals(ShareAccessType.VIEW, shareAccess.getAccessType());
        
        shareAccess.setAccessType(ShareAccessType.DOWNLOAD);
        assertEquals(ShareAccessType.DOWNLOAD, shareAccess.getAccessType());
    }

    @Test
    void testAccessedAtInitialization() {
        ShareAccess newAccess = new ShareAccess();
        LocalDateTime now = LocalDateTime.now();
        
        assertNotNull(newAccess.getAccessedAt());
        assertTrue(newAccess.getAccessedAt().isBefore(now.plusSeconds(1)), 
                "Access timestamp should be initialized to current time");
        assertTrue(newAccess.getAccessedAt().isAfter(now.minusSeconds(1)), 
                "Access timestamp should be initialized to current time");
    }

    @Test
    void testAccessedAtCustomValue() {
        LocalDateTime customTime = LocalDateTime.of(2023, 6, 15, 14, 30, 0);
        shareAccess.setAccessedAt(customTime);
        
        assertEquals(customTime, shareAccess.getAccessedAt());
    }

    @Test
    void testNullFileShare() {
        shareAccess.setFileShare(null);
        assertNull(shareAccess.getFileShare());
    }

    @Test
    void testNullAccessorIp() {
        shareAccess.setAccessorIp(null);
        assertNull(shareAccess.getAccessorIp());
    }

    @Test
    void testLongUserAgent() {
        // Test with a very long user agent string
        StringBuilder longUserAgent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longUserAgent.append("a");
        }
        
        shareAccess.setUserAgent(longUserAgent.toString());
        assertEquals(longUserAgent.toString(), shareAccess.getUserAgent());
    }

    @Test
    void testCompleteAccessLogScenario() {
        // Test a complete access log scenario
        String accessorIp = "203.0.113.42";
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;
        LocalDateTime accessTime = LocalDateTime.now();
        
        ShareAccess completeAccess = new ShareAccess(fileShare, accessorIp, userAgent, accessType);
        completeAccess.setId(123L);
        
        assertEquals(123L, completeAccess.getId());
        assertEquals(fileShare, completeAccess.getFileShare());
        assertEquals(accessorIp, completeAccess.getAccessorIp());
        assertEquals(userAgent, completeAccess.getUserAgent());
        assertEquals(accessType, completeAccess.getAccessType());
        assertNotNull(completeAccess.getAccessedAt());
        assertTrue(completeAccess.getAccessedAt().isAfter(accessTime.minusSeconds(1)));
    }

    @Test
    void testAccessLogWithMinimalData() {
        // Test access log with minimal required data
        ShareAccess minimalAccess = new ShareAccess();
        minimalAccess.setFileShare(fileShare);
        minimalAccess.setAccessType(ShareAccessType.VIEW);
        
        assertEquals(fileShare, minimalAccess.getFileShare());
        assertEquals(ShareAccessType.VIEW, minimalAccess.getAccessType());
        assertNotNull(minimalAccess.getAccessedAt());
        assertNull(minimalAccess.getAccessorIp());
        assertNull(minimalAccess.getUserAgent());
    }
}