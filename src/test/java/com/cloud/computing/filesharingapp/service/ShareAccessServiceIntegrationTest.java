package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.*;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ShareAccessService.
 * 
 * Tests the service with real database interactions to ensure
 * proper functionality in a realistic environment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShareAccessServiceIntegrationTest {

    @Autowired
    private ShareAccessService shareAccessService;

    @Autowired
    private ShareAccessRepository shareAccessRepository;

    @Autowired
    private FileShareRepository fileShareRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private FileEntity testFile;
    private FileShare testShare;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        shareAccessRepository.deleteAll();
        fileShareRepository.deleteAll();
        fileRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("hashedpassword");
        testUser.setEmailVerified(true);
        testUser = userRepository.save(testUser);

        // Create test file
        testFile = new FileEntity();
        testFile.setOriginalFileName("test-document.pdf");
        testFile.setFileName("stored-test-document.pdf");
        testFile.setFileSize(1024L);
        testFile.setContentType("application/pdf");
        testFile.setFilePath("/uploads/test");
        testFile.setUploadTime(LocalDateTime.now());
        testFile.setOwner(testUser);
        testFile = fileRepository.save(testFile);

        // Create test share
        testShare = new FileShare();
        testShare.setFile(testFile);
        testShare.setOwner(testUser);
        testShare.setShareToken("test-token-123");
        testShare.setPermission(SharePermission.DOWNLOAD);
        testShare.setExpiresAt(LocalDateTime.now().plusDays(7));
        testShare.setMaxAccess(100);
        testShare.setActive(true);
        testShare = fileShareRepository.save(testShare);
    }

    @Test
    void logAccess_RealDatabase_ShouldPersistAccessLog() {
        // Arrange
        String accessorIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        ShareAccessType accessType = ShareAccessType.VIEW;

        // Act
        ShareAccess result = shareAccessService.logAccess(testShare, accessorIp, userAgent, accessType);

        // Assert
        assertNotNull(result.getId());

        // Verify persistence
        ShareAccess persisted = shareAccessRepository.findById(result.getId()).orElse(null);
        assertNotNull(persisted);
        assertEquals(accessorIp, persisted.getAccessorIp());
        assertEquals(userAgent, persisted.getUserAgent());
        assertEquals(accessType, persisted.getAccessType());
        assertEquals(testShare.getId(), persisted.getFileShare().getId());
    }

    @Test
    void validateAccess_WithRealRateLimit_ShouldEnforceLimit() {
        // Arrange
        String accessorIp = "192.168.1.200";

        // Create multiple access logs to exceed rate limit
        for (int i = 0; i < 105; i++) {
            ShareAccess access = new ShareAccess(testShare, accessorIp, "TestAgent", ShareAccessType.VIEW);
            access.setAccessedAt(LocalDateTime.now().minusMinutes(30)); // Within rate limit window
            shareAccessRepository.save(access);
        }

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(testShare, accessorIp,
                ShareAccessType.VIEW);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(ShareAccessService.AccessDenialType.RATE_LIMITED, result.getDenialType());
    }

    @Test
    void getAccessStatistics_WithRealData_ShouldReturnAccurateStats() {
        // Arrange - Create various access logs
        String[] ips = { "192.168.1.1", "192.168.1.2", "192.168.1.3" };
        LocalDateTime now = LocalDateTime.now();

        // Create view accesses - some recent (within 24h), some older
        for (int i = 0; i < 15; i++) {
            ShareAccess access = new ShareAccess(testShare, ips[i % 3], "Browser", ShareAccessType.VIEW);
            // Mix of recent and older accesses
            if (i < 10) {
                access.setAccessedAt(now.minusHours(i % 12)); // Recent (within 24h)
            } else {
                access.setAccessedAt(now.minusHours(25 + i)); // Older (beyond 24h)
            }
            shareAccessRepository.save(access);
        }

        // Create download accesses - some recent, some older
        for (int i = 0; i < 8; i++) {
            ShareAccess access = new ShareAccess(testShare, ips[i % 3], "Browser", ShareAccessType.DOWNLOAD);
            if (i < 5) {
                access.setAccessedAt(now.minusHours(i % 6)); // Recent (within 24h)
            } else {
                access.setAccessedAt(now.minusHours(30 + i)); // Older (beyond 24h)
            }
            shareAccessRepository.save(access);
        }

        // Act
        ShareAccessService.ShareAccessStats stats = shareAccessService.getAccessStatistics(testShare);

        // Assert
        assertEquals(23L, stats.getTotalAccesses());
        assertEquals(15L, stats.getViewCount());
        assertEquals(8L, stats.getDownloadCount());
        // Should have 10 recent views + 5 recent downloads = 15 recent accesses
        assertEquals(15L, stats.getRecentAccesses24h());
    }

    @Test
    void getShareAccessHistory_WithRealData_ShouldReturnOrderedHistory() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        ShareAccess oldAccess = new ShareAccess(testShare, "192.168.1.1", "OldBrowser", ShareAccessType.VIEW);
        oldAccess.setAccessedAt(now.minusHours(2));
        shareAccessRepository.save(oldAccess);

        ShareAccess recentAccess = new ShareAccess(testShare, "192.168.1.2", "NewBrowser", ShareAccessType.DOWNLOAD);
        recentAccess.setAccessedAt(now.minusMinutes(30));
        shareAccessRepository.save(recentAccess);

        // Act
        List<ShareAccess> history = shareAccessService.getShareAccessHistory(testShare);

        // Assert
        assertEquals(2, history.size());
        // Should be ordered by access time descending (newest first)
        assertTrue(history.get(0).getAccessedAt().isAfter(history.get(1).getAccessedAt()));
        assertEquals("192.168.1.2", history.get(0).getAccessorIp());
        assertEquals("192.168.1.1", history.get(1).getAccessorIp());
    }

    @Test
    void detectSuspiciousActivity_WithHighFrequencyAccess_ShouldDetectPattern() {
        // Arrange - Create high-frequency access pattern
        String suspiciousIp = "192.168.1.999";
        LocalDateTime now = LocalDateTime.now();

        // Create 60 accesses in the last hour (above threshold)
        for (int i = 0; i < 60; i++) {
            ShareAccess access = new ShareAccess(testShare, suspiciousIp, "SuspiciousAgent", ShareAccessType.VIEW);
            access.setAccessedAt(now.minusMinutes(i));
            shareAccessRepository.save(access);
        }

        // Act
        List<ShareAccessService.SuspiciousActivityReport> reports = shareAccessService
                .detectSuspiciousActivity(suspiciousIp, 1);

        // Assert
        assertEquals(1, reports.size());
        ShareAccessService.SuspiciousActivityReport report = reports.get(0);
        assertEquals(suspiciousIp, report.getIpAddress());
        assertTrue(report.getAccessCount() >= 50); // Above threshold
    }

    @Test
    void getRateLimitStatus_WithRealAccesses_ShouldReturnAccurateStatus() {
        // Arrange
        String testIp = "192.168.1.50";

        // Create 25 accesses in the last hour
        for (int i = 0; i < 25; i++) {
            ShareAccess access = new ShareAccess(testShare, testIp, "TestBrowser", ShareAccessType.VIEW);
            access.setAccessedAt(LocalDateTime.now().minusMinutes(i * 2));
            shareAccessRepository.save(access);
        }

        // Act
        ShareAccessService.RateLimitStatus status = shareAccessService.getRateLimitStatus(testIp);

        // Assert
        assertEquals(testIp, status.getIpAddress());
        assertEquals(25L, status.getCurrentCount());
        assertFalse(status.isLimited()); // Should be under limit (100)
    }

    @Test
    void validateAccess_ExpiredShare_ShouldDenyAccess() {
        // Arrange - Create expired share
        FileShare expiredShare = new FileShare();
        expiredShare.setFile(testFile);
        expiredShare.setOwner(testUser);
        expiredShare.setShareToken("expired-token");
        expiredShare.setPermission(SharePermission.DOWNLOAD);
        expiredShare.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
        expiredShare.setMaxAccess(100);
        expiredShare.setActive(true);
        expiredShare = fileShareRepository.save(expiredShare);

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(expiredShare,
                "192.168.1.1", ShareAccessType.VIEW);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(ShareAccessService.AccessDenialType.PERMISSION_DENIED, result.getDenialType());
    }

    @Test
    void validateAccess_InactiveShare_ShouldDenyAccess() {
        // Arrange - Create inactive share
        testShare.setActive(false);
        fileShareRepository.save(testShare);

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(testShare, "192.168.1.1",
                ShareAccessType.VIEW);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(ShareAccessService.AccessDenialType.PERMISSION_DENIED, result.getDenialType());
    }

    @Test
    void getAccessStatistics_RecentAccessFiltering_ShouldFilterCorrectly() {
        // Arrange - Create accesses with specific timestamps
        LocalDateTime now = LocalDateTime.now();
        String testIp = "192.168.1.100";

        // Create 3 accesses within last 24 hours
        for (int i = 0; i < 3; i++) {
            ShareAccess recentAccess = new ShareAccess(testShare, testIp, "Browser", ShareAccessType.VIEW);
            recentAccess.setAccessedAt(now.minusHours(i + 1)); // 1, 2, 3 hours ago
            shareAccessRepository.save(recentAccess);
        }

        // Create 2 accesses older than 24 hours
        for (int i = 0; i < 2; i++) {
            ShareAccess oldAccess = new ShareAccess(testShare, testIp, "Browser", ShareAccessType.DOWNLOAD);
            oldAccess.setAccessedAt(now.minusHours(25 + i)); // 25, 26 hours ago
            shareAccessRepository.save(oldAccess);
        }

        // Act
        ShareAccessService.ShareAccessStats stats = shareAccessService.getAccessStatistics(testShare);

        // Assert
        assertEquals(5L, stats.getTotalAccesses()); // All accesses
        assertEquals(3L, stats.getViewCount()); // All view accesses
        assertEquals(2L, stats.getDownloadCount()); // All download accesses
        assertEquals(3L, stats.getRecentAccesses24h()); // Only recent accesses (within 24h)
    }

    @Test
    void logAccess_MultipleAccessTypes_ShouldTrackCorrectly() {
        // Arrange
        String accessorIp = "192.168.1.123";
        String userAgent = "TestBrowser/1.0";

        // Act - Log different types of access
        ShareAccess viewAccess = shareAccessService.logAccess(testShare, accessorIp, userAgent, ShareAccessType.VIEW);
        ShareAccess downloadAccess = shareAccessService.logAccess(testShare, accessorIp, userAgent,
                ShareAccessType.DOWNLOAD);

        // Assert
        assertNotNull(viewAccess.getId());
        assertNotNull(downloadAccess.getId());
        assertNotEquals(viewAccess.getId(), downloadAccess.getId());

        // Verify both are persisted with correct types
        List<ShareAccess> allAccesses = shareAccessRepository.findByFileShareOrderByAccessedAtDesc(testShare);
        assertEquals(2, allAccesses.size());

        boolean hasView = allAccesses.stream().anyMatch(a -> a.getAccessType() == ShareAccessType.VIEW);
        boolean hasDownload = allAccesses.stream().anyMatch(a -> a.getAccessType() == ShareAccessType.DOWNLOAD);

        assertTrue(hasView);
        assertTrue(hasDownload);
    }
}