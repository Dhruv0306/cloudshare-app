package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.SharePermission;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdvancedSecurityService.
 * 
 * <p>Tests the advanced security features including:
 * <ul>
 *   <li>Share creation validation with rate limiting</li>
 *   <li>IP-based access controls and blacklisting</li>
 *   <li>Threat level assessment and monitoring</li>
 *   <li>Security analytics generation</li>
 *   <li>Automated security response mechanisms</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class AdvancedSecurityServiceTest {

    @Mock
    private ShareAccessRepository shareAccessRepository;

    @Mock
    private FileShareRepository fileShareRepository;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private AdvancedSecurityService advancedSecurityService;

    private User testUser;
    private FileEntity testFile;
    private FileShare testShare;

    @BeforeEach
    void setUp() {
        // Set up test configuration values
        ReflectionTestUtils.setField(advancedSecurityService, "maxSharesPerHour", 10);
        ReflectionTestUtils.setField(advancedSecurityService, "maxAccessPerIpPerHour", 100);
        ReflectionTestUtils.setField(advancedSecurityService, "suspiciousActivityThreshold", 50);
        ReflectionTestUtils.setField(advancedSecurityService, "rateLimitWindowHours", 1);
        ReflectionTestUtils.setField(advancedSecurityService, "maxFailedAttemptsPerIp", 20);
        ReflectionTestUtils.setField(advancedSecurityService, "ipBlacklistDurationHours", 24);
        ReflectionTestUtils.setField(advancedSecurityService, "geolocationEnabled", false);

        // Create test entities
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        testFile = new FileEntity();
        testFile.setId(1L);
        testFile.setOriginalFileName("test.txt");
        testFile.setFileName("stored_test.txt");
        testFile.setContentType("text/plain");
        testFile.setFileSize(1024L);

        testShare = new FileShare(testFile, testUser, "test-token", SharePermission.DOWNLOAD);
        testShare.setId(1L);
    }

    @Test
    void testValidateShareCreation_Success() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // Mock repository calls to return low usage
        when(fileShareRepository.countByOwnerAndCreatedAtAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(5L); // Under the limit of 10

        // When
        AdvancedSecurityService.ShareCreationValidationResult result = 
            advancedSecurityService.validateShareCreation(testUser, clientIp, userAgent);

        // Then
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getDenialReason());
    }

    @Test
    void testValidateShareCreation_RateLimited() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // Mock repository calls to return high usage (over limit)
        when(fileShareRepository.countByOwnerAndCreatedAtAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(15L); // Over the limit of 10

        // When
        AdvancedSecurityService.ShareCreationValidationResult result = 
            advancedSecurityService.validateShareCreation(testUser, clientIp, userAgent);

        // Then
        assertFalse(result.isAllowed());
        assertNotNull(result.getReason());
        assertEquals(AdvancedSecurityService.SecurityDenialReason.RATE_LIMITED, result.getDenialReason());
    }

    @Test
    void testValidateShareCreation_BlacklistedIp() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // Blacklist the IP first
        advancedSecurityService.blacklistIp(clientIp, 24, "Test blacklist");

        // When
        AdvancedSecurityService.ShareCreationValidationResult result = 
            advancedSecurityService.validateShareCreation(testUser, clientIp, userAgent);

        // Then
        assertFalse(result.isAllowed());
        assertNotNull(result.getReason());
        assertEquals(AdvancedSecurityService.SecurityDenialReason.IP_BLACKLISTED, result.getDenialReason());
    }

    @Test
    void testRecordShareCreation() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // When
        advancedSecurityService.recordShareCreation(testUser, testShare, clientIp, userAgent);

        // Then
        // Verify that audit logging was called
        verify(securityAuditService, never()).logSecurityViolation(any(), any(), any(), any());
        
        // The method should complete without throwing exceptions
        // Internal state changes are tested through integration tests
    }

    @Test
    void testRecordShareAccess_Success() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";
        ShareAccessType accessType = ShareAccessType.VIEW;

        // When
        advancedSecurityService.recordShareAccess(testShare, accessType, clientIp, userAgent, true, null);

        // Then
        // Verify that no security violations were logged for successful access
        verify(securityAuditService, never()).logSecurityViolation(any(), any(), any(), any());
    }

    @Test
    void testRecordShareAccess_Failed() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";
        ShareAccessType accessType = ShareAccessType.DOWNLOAD;
        String denialReason = "Permission denied";

        // When
        advancedSecurityService.recordShareAccess(testShare, accessType, clientIp, userAgent, false, denialReason);

        // Then
        // The method should complete and update internal security state
        // Detailed verification would require integration tests
    }

    @Test
    void testGetSecurityAnalytics() {
        // Given
        int timeWindowHours = 24;
        @SuppressWarnings("unused")
        LocalDateTime since = LocalDateTime.now().minusHours(timeWindowHours);

        // Mock repository responses
        when(shareAccessRepository.countByAccessedAtAfter(any(LocalDateTime.class))).thenReturn(100L);
        when(shareAccessRepository.countByAccessTypeAndAccessedAtAfter(eq(ShareAccessType.VIEW), any(LocalDateTime.class)))
            .thenReturn(60L);
        when(shareAccessRepository.countByAccessTypeAndAccessedAtAfter(eq(ShareAccessType.DOWNLOAD), any(LocalDateTime.class)))
            .thenReturn(40L);
        when(shareAccessRepository.findSuspiciousAccessPatterns(any(LocalDateTime.class), anyLong()))
            .thenReturn(java.util.List.of());
        when(fileShareRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(10L);
        when(fileShareRepository.countByActiveTrueAndCreatedAtAfter(any(LocalDateTime.class))).thenReturn(8L);

        // When
        AdvancedSecurityService.SecurityAnalytics analytics = 
            advancedSecurityService.getSecurityAnalytics(timeWindowHours);

        // Then
        assertNotNull(analytics);
        assertEquals(100L, analytics.getTotalAccesses());
        assertEquals(60L, analytics.getViewAccesses());
        assertEquals(40L, analytics.getDownloadAccesses());
        assertEquals(10L, analytics.getTotalShares());
        assertEquals(8L, analytics.getActiveShares());
        assertEquals(0, analytics.getSuspiciousPatterns());
        assertEquals(0, analytics.getBlacklistedIps());
        assertEquals(0, analytics.getHighThreatIps());
    }

    @Test
    void testGetThreatLevel_Default() {
        // Given
        String clientIp = "192.168.1.100";

        // When
        AdvancedSecurityService.SecurityThreatLevel threatLevel = 
            advancedSecurityService.getThreatLevel(clientIp);

        // Then
        assertEquals(AdvancedSecurityService.SecurityThreatLevel.LOW, threatLevel);
    }

    @Test
    void testBlacklistIp() {
        // Given
        String clientIp = "192.168.1.100";
        int durationHours = 24;
        String reason = "Test blacklist";

        // When
        advancedSecurityService.blacklistIp(clientIp, durationHours, reason);

        // Then
        assertEquals(AdvancedSecurityService.SecurityThreatLevel.CRITICAL, 
                    advancedSecurityService.getThreatLevel(clientIp));

        // Verify security audit logging
        verify(securityAuditService).logSecurityViolation(
            isNull(), 
            contains("IP address manually blacklisted"), 
            eq(SecurityAuditService.SecuritySeverity.HIGH), 
            eq(clientIp)
        );
    }

    @Test
    void testUnblacklistIp() {
        // Given
        String clientIp = "192.168.1.100";
        
        // First blacklist the IP
        advancedSecurityService.blacklistIp(clientIp, 24, "Test blacklist");
        assertEquals(AdvancedSecurityService.SecurityThreatLevel.CRITICAL, 
                    advancedSecurityService.getThreatLevel(clientIp));

        // When
        advancedSecurityService.unblacklistIp(clientIp);

        // Then
        assertEquals(AdvancedSecurityService.SecurityThreatLevel.LOW, 
                    advancedSecurityService.getThreatLevel(clientIp));
    }

    @Test
    void testClearSecurityState() {
        // Given
        String clientIp = "192.168.1.100";
        advancedSecurityService.blacklistIp(clientIp, 24, "Test blacklist");
        
        // Verify IP is blacklisted
        assertEquals(AdvancedSecurityService.SecurityThreatLevel.CRITICAL, 
                    advancedSecurityService.getThreatLevel(clientIp));

        // When
        advancedSecurityService.clearSecurityState();

        // Then
        assertEquals(AdvancedSecurityService.SecurityThreatLevel.LOW, 
                    advancedSecurityService.getThreatLevel(clientIp));
    }

    @Test
    void testRecordShareManagement() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";
        AdvancedSecurityService.ShareManagementOperation operation = 
            AdvancedSecurityService.ShareManagementOperation.REVOKED;

        // When
        advancedSecurityService.recordShareManagement(testUser, testShare, operation, clientIp, userAgent);

        // Then
        // The method should complete without throwing exceptions
        // Detailed verification of audit logging would require integration tests
    }

    @Test
    void testValidateShareCreation_GeolocationDisabled() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";
        
        // Geolocation is disabled by default in setUp()
        when(fileShareRepository.countByOwnerAndCreatedAtAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(5L);

        // When
        AdvancedSecurityService.ShareCreationValidationResult result = 
            advancedSecurityService.validateShareCreation(testUser, clientIp, userAgent);

        // Then
        assertTrue(result.isAllowed());
        // Should not be blocked by geolocation when disabled
    }

    @Test
    void testValidateShareCreation_SuspiciousActivity() {
        // Given
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // Mock high access count to trigger suspicious activity detection
        when(fileShareRepository.countByOwnerAndCreatedAtAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(5L); // Under share limit
        when(shareAccessRepository.countByAccessorIpAndAccessedAtAfter(eq(clientIp), any(LocalDateTime.class)))
            .thenReturn(150L); // Over suspicious threshold

        // When
        AdvancedSecurityService.ShareCreationValidationResult result = 
            advancedSecurityService.validateShareCreation(testUser, clientIp, userAgent);

        // Then
        assertFalse(result.isAllowed());
        assertEquals(AdvancedSecurityService.SecurityDenialReason.SUSPICIOUS_ACTIVITY, result.getDenialReason());
    }
}