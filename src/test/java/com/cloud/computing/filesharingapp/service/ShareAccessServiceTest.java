package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccess;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.SharePermission;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShareAccessService.
 * 
 * Tests cover access logging, rate limiting, security validation,
 * and statistics generation functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareAccessServiceTest {

    @Mock
    private ShareAccessRepository shareAccessRepository;

    @InjectMocks
    private ShareAccessService shareAccessService;

    private FileShare validFileShare;
    private FileShare expiredFileShare;
    private FileShare viewOnlyFileShare;
    private ShareAccess sampleShareAccess;

    @BeforeEach
    void setUp() {
        // Set up configuration values
        ReflectionTestUtils.setField(shareAccessService, "maxAccessPerIpPerHour", 100);
        ReflectionTestUtils.setField(shareAccessService, "maxAccessPerSharePerIpPerHour", 20);
        ReflectionTestUtils.setField(shareAccessService, "suspiciousActivityThreshold", 50);
        ReflectionTestUtils.setField(shareAccessService, "rateLimitWindowHours", 1);

        // Create test file shares
        validFileShare = createValidFileShare();
        expiredFileShare = createExpiredFileShare();
        viewOnlyFileShare = createViewOnlyFileShare();

        // Create sample share access
        sampleShareAccess = new ShareAccess(validFileShare, "192.168.1.1", "Mozilla/5.0", ShareAccessType.VIEW);
        sampleShareAccess.setId(1L);
    }

    @Test
    void logAccess_ValidRequest_ShouldCreateAccessLog() {
        // Arrange
        String accessorIp = "192.168.1.1";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
        ShareAccessType accessType = ShareAccessType.VIEW;

        when(shareAccessRepository.save(any(ShareAccess.class))).thenReturn(sampleShareAccess);

        // Act
        ShareAccess result = shareAccessService.logAccess(validFileShare, accessorIp, userAgent, accessType);

        // Assert
        assertNotNull(result);
        assertEquals(sampleShareAccess.getId(), result.getId());
        verify(shareAccessRepository).save(any(ShareAccess.class));
    }

    @Test
    void logAccess_DownloadRequest_ShouldLogDownloadAccess() {
        // Arrange
        String accessorIp = "10.0.0.1";
        String userAgent = "Chrome/91.0";
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;

        ShareAccess downloadAccess = new ShareAccess(validFileShare, accessorIp, userAgent, accessType);
        downloadAccess.setId(2L);

        when(shareAccessRepository.save(any(ShareAccess.class))).thenReturn(downloadAccess);

        // Act
        ShareAccess result = shareAccessService.logAccess(validFileShare, accessorIp, userAgent, accessType);

        // Assert
        assertNotNull(result);
        assertEquals(downloadAccess.getId(), result.getId());
        verify(shareAccessRepository).save(argThat(access -> access.getAccessType() == ShareAccessType.DOWNLOAD &&
                access.getAccessorIp().equals(accessorIp)));
    }

    @Test
    void validateAccess_ValidShare_ShouldAllowAccess() {
        // Arrange
        String accessorIp = "192.168.1.1";
        ShareAccessType accessType = ShareAccessType.VIEW;

        when(shareAccessRepository.countByAccessorIpAndAccessedAtAfter(eq(accessorIp), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(shareAccessRepository.findSuspiciousAccessPatterns(eq(accessorIp), any(LocalDateTime.class), anyLong()))
                .thenReturn(List.of());

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(validFileShare, accessorIp,
                accessType);

        // Assert
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getDenialType());
    }

    @Test
    void validateAccess_ExpiredShare_ShouldDenyAccess() {
        // Arrange
        String accessorIp = "192.168.1.1";
        ShareAccessType accessType = ShareAccessType.VIEW;

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(expiredFileShare,
                accessorIp, accessType);

        // Assert
        assertFalse(result.isAllowed());
        assertNotNull(result.getReason());
        assertEquals(ShareAccessService.AccessDenialType.PERMISSION_DENIED, result.getDenialType());
    }

    @Test
    void validateAccess_DownloadOnViewOnlyShare_ShouldDenyAccess() {
        // Arrange
        String accessorIp = "192.168.1.1";
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(viewOnlyFileShare,
                accessorIp, accessType);

        // Assert
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Download permission not granted"));
        assertEquals(ShareAccessService.AccessDenialType.PERMISSION_DENIED, result.getDenialType());
    }

    @Test
    void validateAccess_RateLimitExceeded_ShouldDenyAccess() {
        // Arrange
        String accessorIp = "192.168.1.1";
        ShareAccessType accessType = ShareAccessType.VIEW;

        // Mock rate limit exceeded
        when(shareAccessRepository.countByAccessorIpAndAccessedAtAfter(eq(accessorIp), any(LocalDateTime.class)))
                .thenReturn(150L); // Exceeds maxAccessPerIpPerHour (100)

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(validFileShare, accessorIp,
                accessType);

        // Assert
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Too many access attempts"));
        assertEquals(ShareAccessService.AccessDenialType.RATE_LIMITED, result.getDenialType());
    }

    @Test
    void validateAccess_SuspiciousActivity_ShouldDenyAccess() {
        // Arrange
        String accessorIp = "192.168.1.1";
        ShareAccessType accessType = ShareAccessType.VIEW;

        // Mock normal rate limit (under threshold)
        when(shareAccessRepository.countByAccessorIpAndAccessedAtAfter(eq(accessorIp), any(LocalDateTime.class)))
                .thenReturn(5L);

        // Mock suspicious activity detected - return non-empty list to trigger
        // suspicious activity
        Object[] suspiciousPattern = { accessorIp, 60L };
        when(shareAccessRepository.findSuspiciousAccessPatterns(eq(accessorIp), any(LocalDateTime.class), eq(50L)))
                .thenReturn(List.<Object[]>of(suspiciousPattern));

        // Act
        ShareAccessService.AccessValidationResult result = shareAccessService.validateAccess(validFileShare, accessorIp,
                accessType);

        // Assert
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Suspicious activity"));
        assertEquals(ShareAccessService.AccessDenialType.SUSPICIOUS_ACTIVITY, result.getDenialType());
    }

    @Test
    void getShareAccessHistory_ValidShare_ShouldReturnHistory() {
        // Arrange
        List<ShareAccess> expectedHistory = Arrays.asList(
                sampleShareAccess,
                new ShareAccess(validFileShare, "10.0.0.1", "Chrome", ShareAccessType.DOWNLOAD));

        when(shareAccessRepository.findByFileShareOrderByAccessedAtDesc(validFileShare))
                .thenReturn(expectedHistory);

        // Act
        List<ShareAccess> result = shareAccessService.getShareAccessHistory(validFileShare);

        // Assert
        assertEquals(expectedHistory.size(), result.size());
        assertEquals(expectedHistory, result);
        verify(shareAccessRepository).findByFileShareOrderByAccessedAtDesc(validFileShare);
    }

    @Test
    void getShareAccessHistory_WithAccessType_ShouldReturnFilteredHistory() {
        // Arrange
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;
        List<ShareAccess> expectedHistory = Arrays.asList(
                new ShareAccess(validFileShare, "10.0.0.1", "Chrome", ShareAccessType.DOWNLOAD));

        when(shareAccessRepository.findByFileShareAndAccessTypeOrderByAccessedAtDesc(validFileShare, accessType))
                .thenReturn(expectedHistory);

        // Act
        List<ShareAccess> result = shareAccessService.getShareAccessHistory(validFileShare, accessType);

        // Assert
        assertEquals(expectedHistory.size(), result.size());
        assertEquals(expectedHistory, result);
        verify(shareAccessRepository).findByFileShareAndAccessTypeOrderByAccessedAtDesc(validFileShare, accessType);
    }

    @Test
    void getAccessStatistics_ValidShare_ShouldReturnStats() {
        // Arrange
        when(shareAccessRepository.countByFileShare(validFileShare)).thenReturn(100L);
        when(shareAccessRepository.countByFileShareAndAccessType(validFileShare, ShareAccessType.VIEW)).thenReturn(70L);
        when(shareAccessRepository.countByFileShareAndAccessType(validFileShare, ShareAccessType.DOWNLOAD))
                .thenReturn(30L);
        
        // Mock all accesses with mixed timestamps to test filtering
        LocalDateTime now = LocalDateTime.now();
        ShareAccess recentAccess1 = new ShareAccess(validFileShare, "192.168.1.1", "Mozilla/5.0", ShareAccessType.VIEW);
        recentAccess1.setAccessedAt(now.minusHours(1)); // Within last 24 hours
        
        ShareAccess recentAccess2 = new ShareAccess(validFileShare, "192.168.1.2", "Chrome", ShareAccessType.DOWNLOAD);
        recentAccess2.setAccessedAt(now.minusHours(12)); // Within last 24 hours
        
        ShareAccess oldAccess = new ShareAccess(validFileShare, "192.168.1.3", "Safari", ShareAccessType.VIEW);
        oldAccess.setAccessedAt(now.minusHours(30)); // Older than 24 hours
        
        when(shareAccessRepository.findByFileShareOrderByAccessedAtDesc(validFileShare))
                .thenReturn(Arrays.asList(recentAccess1, recentAccess2, oldAccess));
        
        when(shareAccessRepository.getAccessStatsByTypeAndPeriod(eq(validFileShare), any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of(new Object[] { "VIEW", 50L }, new Object[] { "DOWNLOAD", 20L }));

        // Act
        ShareAccessService.ShareAccessStats result = shareAccessService.getAccessStatistics(validFileShare);

        // Assert
        assertEquals(100L, result.getTotalAccesses());
        assertEquals(70L, result.getViewCount());
        assertEquals(30L, result.getDownloadCount());
        assertEquals(2L, result.getRecentAccesses24h()); // Only 2 recent accesses (within 24h)
        assertEquals(2, result.getWeeklyStatsByType().size());
    }

    @Test
    void detectSuspiciousActivity_SpecificIp_ShouldReturnReports() {
        // Arrange
        String suspiciousIp = "192.168.1.100";
        Object[] pattern = { suspiciousIp, 75L };

        when(shareAccessRepository.findSuspiciousAccessPatterns(eq(suspiciousIp), any(LocalDateTime.class), eq(50L)))
                .thenReturn(List.<Object[]>of(pattern));

        // Act
        List<ShareAccessService.SuspiciousActivityReport> result = shareAccessService
                .detectSuspiciousActivity(suspiciousIp, 1);

        // Assert
        assertEquals(1, result.size());
        ShareAccessService.SuspiciousActivityReport report = result.get(0);
        assertEquals(suspiciousIp, report.getIpAddress());
        assertEquals(75L, report.getAccessCount());
        assertNotNull(report.getDescription());
    }

    @Test
    void getRateLimitStatus_ValidIp_ShouldReturnStatus() {
        // Arrange
        String accessorIp = "192.168.1.1";
        when(shareAccessRepository.countByAccessorIpAndAccessedAtAfter(eq(accessorIp), any(LocalDateTime.class)))
                .thenReturn(25L);

        // Act
        ShareAccessService.RateLimitStatus result = shareAccessService.getRateLimitStatus(accessorIp);

        // Assert
        assertEquals(accessorIp, result.getIpAddress());
        assertEquals(25L, result.getCurrentCount());
        assertEquals(100L, result.getLimit());
        assertFalse(result.isLimited());
    }

    @Test
    void getRateLimitStatus_ExceededLimit_ShouldShowLimited() {
        // Arrange
        String accessorIp = "192.168.1.1";
        when(shareAccessRepository.countByAccessorIpAndAccessedAtAfter(eq(accessorIp), any(LocalDateTime.class)))
                .thenReturn(150L);

        // Act
        ShareAccessService.RateLimitStatus result = shareAccessService.getRateLimitStatus(accessorIp);

        // Assert
        assertEquals(accessorIp, result.getIpAddress());
        assertEquals(150L, result.getCurrentCount());
        assertEquals(100L, result.getLimit());
        assertTrue(result.isLimited());
    }

    @Test
    void getAccessStatistics_NoRecentAccesses_ShouldReturnZeroRecent() {
        // Arrange
        when(shareAccessRepository.countByFileShare(validFileShare)).thenReturn(50L);
        when(shareAccessRepository.countByFileShareAndAccessType(validFileShare, ShareAccessType.VIEW)).thenReturn(30L);
        when(shareAccessRepository.countByFileShareAndAccessType(validFileShare, ShareAccessType.DOWNLOAD))
                .thenReturn(20L);
        
        // Mock all accesses as older than 24 hours
        LocalDateTime now = LocalDateTime.now();
        ShareAccess oldAccess1 = new ShareAccess(validFileShare, "192.168.1.1", "Mozilla/5.0", ShareAccessType.VIEW);
        oldAccess1.setAccessedAt(now.minusHours(30)); // Older than 24 hours
        
        ShareAccess oldAccess2 = new ShareAccess(validFileShare, "192.168.1.2", "Chrome", ShareAccessType.DOWNLOAD);
        oldAccess2.setAccessedAt(now.minusDays(2)); // Much older
        
        when(shareAccessRepository.findByFileShareOrderByAccessedAtDesc(validFileShare))
                .thenReturn(Arrays.asList(oldAccess1, oldAccess2));
        
        when(shareAccessRepository.getAccessStatsByTypeAndPeriod(eq(validFileShare), any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        ShareAccessService.ShareAccessStats result = shareAccessService.getAccessStatistics(validFileShare);

        // Assert
        assertEquals(50L, result.getTotalAccesses());
        assertEquals(30L, result.getViewCount());
        assertEquals(20L, result.getDownloadCount());
        assertEquals(0L, result.getRecentAccesses24h()); // No recent accesses
        assertEquals(0, result.getWeeklyStatsByType().size());
    }

    @Test
    void clearRateLimitCache_ShouldClearCache() {
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> shareAccessService.clearRateLimitCache());
    }

    // Helper methods for creating test data

    private FileShare createValidFileShare() {
        FileShare share = new FileShare();
        share.setId(1L);
        share.setActive(true);
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setPermission(SharePermission.DOWNLOAD);
        share.setMaxAccess(1000);
        share.setAccessCount(0);
        return share;
    }

    private FileShare createExpiredFileShare() {
        FileShare share = new FileShare();
        share.setId(2L);
        share.setActive(true);
        share.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
        share.setPermission(SharePermission.DOWNLOAD);
        share.setMaxAccess(1000);
        share.setAccessCount(0);
        return share;
    }

    private FileShare createViewOnlyFileShare() {
        FileShare share = new FileShare();
        share.setId(3L);
        share.setActive(true);
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setPermission(SharePermission.VIEW_ONLY);
        share.setMaxAccess(1000);
        share.setAccessCount(0);
        return share;
    }
}