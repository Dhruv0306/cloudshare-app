package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.config.MaintenanceConfig;
import com.cloud.computing.filesharingapp.dto.ShareAnalyticsDTO;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MaintenanceTaskService.
 * 
 * <p>
 * This test class verifies the functionality of automated maintenance tasks
 * including cleanup operations, analytics generation, and health monitoring.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceTaskServiceTest {

    @Mock
    private FileShareRepository fileShareRepository;

    @Mock
    private ShareAccessRepository shareAccessRepository;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private ShareAnalyticsService shareAnalyticsService;

    @Mock
    private MaintenanceConfig maintenanceConfig;

    @InjectMocks
    private MaintenanceTaskService maintenanceTaskService;

    @BeforeEach
    void setUp() {
        // Set up default configuration values
        lenient().when(maintenanceConfig.getAccessLogRetentionDays()).thenReturn(90);
        lenient().when(maintenanceConfig.getVerificationRetentionDays()).thenReturn(30);
        lenient().when(maintenanceConfig.getMaxQueryTimeMs()).thenReturn(1000L);
        lenient().when(maintenanceConfig.getSuspiciousAccessThreshold()).thenReturn(50);
        lenient().when(maintenanceConfig.getMaxSuspiciousIps()).thenReturn(5);
    }

    /**
     * Tests the cleanup of expired shares functionality.
     * 
     * <p>
     * Verifies that the cleanup process correctly:
     * <ul>
     * <li>Deactivates expired shares</li>
     * <li>Deactivates shares that reached max access count</li>
     * <li>Removes old access logs</li>
     * <li>Logs maintenance events</li>
     * </ul>
     */
    @Test
    void testCleanupExpiredShares() {
        // Arrange
        when(fileShareRepository.deactivateExpiredShares(any(LocalDateTime.class))).thenReturn(5);
        when(fileShareRepository.deactivateMaxAccessReachedShares()).thenReturn(3);
        when(shareAccessRepository.count()).thenReturn(1000L).thenReturn(900L);
        when(shareAccessRepository.deleteByAccessedAtBefore(any(LocalDateTime.class))).thenReturn(100);

        // Act
        maintenanceTaskService.cleanupExpiredShares();

        // Assert
        verify(fileShareRepository).deactivateExpiredShares(any(LocalDateTime.class));
        verify(fileShareRepository).deactivateMaxAccessReachedShares();
        verify(shareAccessRepository).deleteByAccessedAtBefore(any(LocalDateTime.class));
        verify(securityAuditService).logMaintenanceEvent(eq("SHARE_CLEANUP"), eq(8), anyString());
        verify(securityAuditService).logPerformanceMetrics(eq("SHARE_CLEANUP"), anyLong(), eq(108));
    }

    /**
     * Tests the share analytics generation functionality.
     * 
     * <p>
     * Verifies that analytics are properly generated and logged.
     */
    @Test
    void testGenerateShareAnalytics() {
        // Arrange
        ShareAnalyticsDTO mockAnalytics = new ShareAnalyticsDTO();
        mockAnalytics.setTotalShares(100);
        mockAnalytics.setTotalAccesses24h(50);
        when(shareAnalyticsService.generateComprehensiveAnalytics()).thenReturn(mockAnalytics);

        // Act
        maintenanceTaskService.generateShareAnalytics();

        // Assert
        verify(shareAnalyticsService).generateComprehensiveAnalytics();
        verify(shareAnalyticsService).logAnalyticsSummary(any());
        verify(securityAuditService).logMaintenanceEvent(eq("SHARE_ANALYTICS"), anyInt(), anyString());
        verify(securityAuditService).logPerformanceMetrics(eq("SHARE_ANALYTICS"), anyLong(), anyInt());
    }

    /**
     * Tests the system health monitoring functionality.
     * 
     * <p>
     * Verifies that health metrics are properly collected and logged.
     */
    @Test
    void testMonitorSharingSystemHealth() {
        // Arrange
        ShareAnalyticsDTO mockHealthMetrics = new ShareAnalyticsDTO();
        mockHealthMetrics.setActiveShares(50);
        mockHealthMetrics.setQueryPerformanceMs(500);
        mockHealthMetrics.setSuspiciousIpCount(2);
        mockHealthMetrics.setHealthStatus("HEALTHY");
        when(shareAnalyticsService.getQuickHealthMetrics()).thenReturn(mockHealthMetrics);
        when(fileShareRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(10L);
        when(shareAccessRepository.countByAccessedAtAfter(any(LocalDateTime.class))).thenReturn(50L);

        // Act
        maintenanceTaskService.monitorSharingSystemHealth();

        // Assert
        verify(shareAnalyticsService).getQuickHealthMetrics();
        verify(shareAnalyticsService).logAnalyticsSummary(any());
        verify(securityAuditService).logMaintenanceEvent(eq("SHARING_HEALTH_CHECK"), anyInt(), anyString());
        verify(securityAuditService).logPerformanceMetrics(eq("SHARING_HEALTH_CHECK"), anyLong(), anyInt());
    }

    /**
     * Tests the manual execution of all maintenance tasks.
     * 
     * <p>
     * Verifies that all maintenance tasks are executed when triggered manually.
     */
    @Test
    void testPerformAllMaintenanceTasks() {
        // Arrange
        ShareAnalyticsDTO mockAnalytics = new ShareAnalyticsDTO();
        mockAnalytics.setTotalShares(100);
        mockAnalytics.setTotalAccesses24h(50);
        ShareAnalyticsDTO mockHealthMetrics = new ShareAnalyticsDTO();
        mockHealthMetrics.setActiveShares(50);
        mockHealthMetrics.setQueryPerformanceMs(500);
        mockHealthMetrics.setHealthStatus("HEALTHY");

        when(shareAnalyticsService.generateComprehensiveAnalytics()).thenReturn(mockAnalytics);
        when(shareAnalyticsService.getQuickHealthMetrics()).thenReturn(mockHealthMetrics);
        when(fileShareRepository.deactivateExpiredShares(any(LocalDateTime.class))).thenReturn(2);
        when(fileShareRepository.deactivateMaxAccessReachedShares()).thenReturn(1);
        when(shareAccessRepository.count()).thenReturn(500L).thenReturn(450L);
        when(shareAccessRepository.deleteByAccessedAtBefore(any(LocalDateTime.class))).thenReturn(50);

        // Act
        maintenanceTaskService.performAllMaintenanceTasks();

        // Assert
        // Verify that sharing maintenance tasks are called
        verify(shareAnalyticsService, atLeastOnce()).generateComprehensiveAnalytics();
        verify(shareAnalyticsService, atLeastOnce()).getQuickHealthMetrics();

        // Verify that cleanup operations are performed
        verify(fileShareRepository).deactivateExpiredShares(any(LocalDateTime.class));
        verify(fileShareRepository).deactivateMaxAccessReachedShares();
    }

    /**
     * Tests the manual execution of sharing-specific maintenance tasks.
     * 
     * <p>
     * Verifies that only sharing-related maintenance tasks are executed.
     */
    @Test
    void testPerformSharingMaintenanceTasks() {
        // Arrange
        ShareAnalyticsDTO mockAnalytics = new ShareAnalyticsDTO();
        mockAnalytics.setTotalShares(50);
        mockAnalytics.setTotalAccesses24h(25);
        ShareAnalyticsDTO mockHealthMetrics = new ShareAnalyticsDTO();
        mockHealthMetrics.setActiveShares(25);
        mockHealthMetrics.setQueryPerformanceMs(300);
        mockHealthMetrics.setHealthStatus("HEALTHY");

        when(shareAnalyticsService.generateComprehensiveAnalytics()).thenReturn(mockAnalytics);
        when(shareAnalyticsService.getQuickHealthMetrics()).thenReturn(mockHealthMetrics);
        when(fileShareRepository.deactivateExpiredShares(any(LocalDateTime.class))).thenReturn(1);
        when(fileShareRepository.deactivateMaxAccessReachedShares()).thenReturn(0);
        when(shareAccessRepository.count()).thenReturn(200L).thenReturn(180L);
        when(shareAccessRepository.deleteByAccessedAtBefore(any(LocalDateTime.class))).thenReturn(20);

        // Act
        maintenanceTaskService.performSharingMaintenanceTasks();

        // Assert
        verify(shareAnalyticsService).generateComprehensiveAnalytics();
        verify(shareAnalyticsService).getQuickHealthMetrics();
        verify(fileShareRepository).deactivateExpiredShares(any(LocalDateTime.class));
        verify(fileShareRepository).deactivateMaxAccessReachedShares();
    }
}