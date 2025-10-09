package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.config.MaintenanceConfig;
import com.cloud.computing.filesharingapp.repository.EmailVerificationRepository;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import com.cloud.computing.filesharingapp.repository.ShareNotificationRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for handling scheduled maintenance tasks related to email verification,
 * user account management, and file sharing system maintenance.
 * 
 * <p>This service provides automated cleanup and maintenance operations including:
 * <ul>
 *   <li>Cleanup of expired file shares and inactive shares</li>
 *   <li>Database performance optimization with proper indexing</li>
 *   <li>Share usage analytics and reporting</li>
 *   <li>System health monitoring and alerting</li>
 *   <li>Performance metrics tracking</li>
 * </ul>
 * 
 * <p>All maintenance tasks are scheduled to run at optimal times to minimize
 * impact on system performance and user experience.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class MaintenanceTaskService {

    private static final Logger logger = LoggerFactory.getLogger(MaintenanceTaskService.class);

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileShareRepository fileShareRepository;

    @Autowired
    private ShareAccessRepository shareAccessRepository;

    @SuppressWarnings("unused")
    @Autowired
    private ShareNotificationRepository shareNotificationRepository;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private ShareAnalyticsService shareAnalyticsService;

    @Autowired
    private MaintenanceConfig maintenanceConfig;

    /**
     * Scheduled task to clean up expired and inactive file shares.
     * 
     * <p>This task performs the following cleanup operations:
     * <ul>
     *   <li>Deactivates shares that have passed their expiration time</li>
     *   <li>Deactivates shares that have reached their maximum access count</li>
     *   <li>Removes old access logs (older than 90 days) to manage database size</li>
     *   <li>Logs cleanup statistics for monitoring</li>
     * </ul>
     * 
     * <p>Runs every 2 hours to ensure timely cleanup of expired shares.
     */
    @Scheduled(fixedRate = 7200000) // Run every 2 hours (7200000 ms)
    @Transactional
    @ConditionalOnProperty(name = "app.maintenance.cleanup-enabled", havingValue = "true", matchIfMissing = true)
    public void cleanupExpiredShares() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting cleanup of expired file shares");

        try {
            LocalDateTime currentTime = LocalDateTime.now();
            
            // Deactivate expired shares
            int expiredSharesCount = fileShareRepository.deactivateExpiredShares(currentTime);
            
            // Deactivate shares that reached max access count
            int maxAccessReachedCount = fileShareRepository.deactivateMaxAccessReachedShares();
            
            // Clean up old access logs using configured retention period
            LocalDateTime retentionCutoff = currentTime.minusDays(maintenanceConfig.getAccessLogRetentionDays());
            long oldAccessLogsCountBefore = shareAccessRepository.count();
            shareAccessRepository.deleteByAccessedAtBefore(retentionCutoff);
            long oldAccessLogsCountAfter = shareAccessRepository.count();
            int deletedAccessLogs = (int) (oldAccessLogsCountBefore - oldAccessLogsCountAfter);

            int totalCleaned = expiredSharesCount + maxAccessReachedCount;
            
            if (totalCleaned > 0 || deletedAccessLogs > 0) {
                logger.info("Share cleanup completed: {} expired shares, {} max-access shares, {} old access logs", 
                    expiredSharesCount, maxAccessReachedCount, deletedAccessLogs);
                
                // Log maintenance event
                String cleanupDetails = String.format(
                    "Expired shares: %d, Max-access shares: %d, Old access logs: %d (retention: %d days)",
                    expiredSharesCount, maxAccessReachedCount, deletedAccessLogs, 
                    maintenanceConfig.getAccessLogRetentionDays()
                );
                securityAuditService.logMaintenanceEvent("SHARE_CLEANUP", totalCleaned, cleanupDetails);
            } else {
                logger.debug("No expired shares or old access logs found for cleanup");
            }

            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log performance metrics
            securityAuditService.logPerformanceMetrics("SHARE_CLEANUP", 
                executionTime, totalCleaned + deletedAccessLogs);

        } catch (Exception e) {
            logger.error("Error during share cleanup: {}", e.getMessage(), e);
            
            // Log maintenance failure
            securityAuditService.logMaintenanceEvent("SHARE_CLEANUP", 0,
                "Share cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Scheduled task to generate comprehensive share usage analytics and reporting.
     * 
     * <p>This task generates detailed analytics including:
     * <ul>
     *   <li>Total shares created, active, and expired counts</li>
     *   <li>Access statistics (views vs downloads) for the last 24 hours</li>
     *   <li>Top accessed shares and usage patterns</li>
     *   <li>System performance metrics and health indicators</li>
     * </ul>
     * 
     * <p>Runs every 4 hours to provide regular usage insights.
     */
    @Scheduled(fixedRate = 14400000) // Run every 4 hours (14400000 ms)
    @ConditionalOnProperty(name = "app.maintenance.analytics-enabled", havingValue = "true", matchIfMissing = true)
    public void generateShareAnalytics() {
        long startTime = System.currentTimeMillis();
        logger.info("Generating share usage analytics and reporting");

        try {
            // Generate comprehensive analytics using the dedicated service
            var analytics = shareAnalyticsService.generateComprehensiveAnalytics();
            
            // Log the analytics summary
            shareAnalyticsService.logAnalyticsSummary(analytics);

            long executionTime = System.currentTimeMillis() - startTime;

            // Log as maintenance event
            securityAuditService.logMaintenanceEvent("SHARE_ANALYTICS", 
                (int) analytics.getTotalAccesses24h(), analytics.getAnalyticsSummary());

            // Log performance metrics
            securityAuditService.logPerformanceMetrics("SHARE_ANALYTICS", 
                executionTime, (int) analytics.getTotalShares());

        } catch (Exception e) {
            logger.error("Error generating share analytics: {}", e.getMessage(), e);
            
            // Log analytics failure
            securityAuditService.logMaintenanceEvent("SHARE_ANALYTICS", 0,
                "Analytics generation failed: " + e.getMessage());
        }
    }

    /**
     * Scheduled task to monitor file sharing system health and performance.
     * 
     * <p>This task monitors various health indicators including:
     * <ul>
     *   <li>Database query performance for share operations</li>
     *   <li>Share creation and access rates</li>
     *   <li>Error rates and system stability metrics</li>
     *   <li>Storage usage and capacity planning indicators</li>
     * </ul>
     * 
     * <p>Runs every 6 hours to provide regular health monitoring.
     */
    @Scheduled(fixedRate = 21600000) // Run every 6 hours (21600000 ms)
    @ConditionalOnProperty(name = "app.maintenance.health-monitoring-enabled", havingValue = "true", matchIfMissing = true)
    public void monitorSharingSystemHealth() {
        long startTime = System.currentTimeMillis();
        logger.info("Monitoring file sharing system health");

        try {
            // Get quick health metrics using the dedicated service
            var healthMetrics = shareAnalyticsService.getQuickHealthMetrics();
            
            // Log the health status
            shareAnalyticsService.logAnalyticsSummary(healthMetrics);

            // Additional detailed monitoring
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime oneHourAgo = currentTime.minusHours(1);
            LocalDateTime oneDayAgo = currentTime.minusDays(1);

            long recentShares = fileShareRepository.countByCreatedAtAfter(oneHourAgo);
            long dailyShares = fileShareRepository.countByCreatedAtAfter(oneDayAgo);
            long dailyAccesses = shareAccessRepository.countByAccessedAtAfter(oneDayAgo);

            String detailedHealthReport = String.format(
                "Detailed Health: Recent Shares: %d (1h), Daily: %d shares, %d accesses | " +
                "Performance: %dms query time | Security: %d suspicious IPs | Status: %s",
                recentShares, dailyShares, dailyAccesses,
                healthMetrics.getQueryPerformanceMs(), healthMetrics.getSuspiciousIpCount(),
                healthMetrics.getHealthStatus()
            );

            if ("HEALTHY".equals(healthMetrics.getHealthStatus())) {
                logger.info(detailedHealthReport);
            } else {
                logger.warn(detailedHealthReport + " - Issues: " + healthMetrics.getHealthIssues());
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // Log health monitoring event
            securityAuditService.logMaintenanceEvent("SHARING_HEALTH_CHECK", 
                "HEALTHY".equals(healthMetrics.getHealthStatus()) ? 1 : 0, detailedHealthReport);

            // Log performance metrics
            securityAuditService.logPerformanceMetrics("SHARING_HEALTH_CHECK", 
                executionTime, (int) healthMetrics.getActiveShares());

        } catch (Exception e) {
            logger.error("Error during sharing system health monitoring: {}", e.getMessage(), e);
            
            // Log health check failure
            securityAuditService.logMaintenanceEvent("SHARING_HEALTH_CHECK", 0,
                "Health monitoring failed: " + e.getMessage());
        }
    }

    /**
     * Scheduled task to clean up pending user accounts that haven't been verified
     * within 24 hours of registration.
     * Runs every 6 hours.
     */
    @Scheduled(fixedRate = 21600000) // Run every 6 hours (21600000 ms)
    @Transactional
    public void cleanupPendingAccounts() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting cleanup of pending user accounts");

        try {
            LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
            
            // Find users with PENDING status created more than 24 hours ago
            long pendingUsersCount = userRepository.countPendingUsersOlderThan(twentyFourHoursAgo);
            
            if (pendingUsersCount > 0) {
                // Delete pending users and their associated verification records
                userRepository.deletePendingUsersOlderThan(twentyFourHoursAgo);
                
                logger.info("Deleted {} pending user accounts older than 24 hours", pendingUsersCount);
                
                // Log maintenance event
                securityAuditService.logMaintenanceEvent("PENDING_ACCOUNTS_CLEANUP", (int) pendingUsersCount,
                    "Deleted pending user accounts older than 24 hours");
            } else {
                logger.debug("No pending user accounts found for cleanup");
            }

            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log performance metrics
            securityAuditService.logPerformanceMetrics("PENDING_ACCOUNTS_CLEANUP", 
                executionTime, (int) pendingUsersCount);

        } catch (Exception e) {
            logger.error("Error during pending accounts cleanup: {}", e.getMessage(), e);
            
            // Log maintenance failure
            securityAuditService.logMaintenanceEvent("PENDING_ACCOUNTS_CLEANUP", 0,
                "Cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Scheduled task to generate and log system health metrics.
     * Runs every 12 hours.
     */
    @Scheduled(fixedRate = 43200000) // Run every 12 hours (43200000 ms)
    public void generateHealthMetrics() {
        long startTime = System.currentTimeMillis();
        logger.info("Generating system health metrics");

        try {
            // Get verification statistics
            long totalVerifications = emailVerificationRepository.count();
            long activeVerifications = emailVerificationRepository.countByUsedFalse();
            long expiredVerifications = emailVerificationRepository
                .findByExpiresAtBeforeAndUsedFalse(LocalDateTime.now()).size();

            // Get user statistics
            long totalUsers = userRepository.count();
            long verifiedUsers = userRepository.countByEmailVerifiedTrue();
            long pendingUsers = userRepository.countByEmailVerifiedFalse();

            // Log health metrics
            String healthReport = String.format(
                "System Health: Total Users: %d, Verified: %d, Pending: %d, " +
                "Total Verifications: %d, Active: %d, Expired: %d",
                totalUsers, verifiedUsers, pendingUsers,
                totalVerifications, activeVerifications, expiredVerifications
            );

            logger.info(healthReport);

            long executionTime = System.currentTimeMillis() - startTime;

            // Log as maintenance event
            securityAuditService.logMaintenanceEvent("HEALTH_METRICS", 0, healthReport);

            // Log performance metrics
            securityAuditService.logPerformanceMetrics("HEALTH_METRICS", executionTime, 0);

        } catch (Exception e) {
            logger.error("Error generating health metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to perform database optimization tasks.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2:00 AM
    @Transactional
    public void performDatabaseOptimization() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting database optimization tasks");

        try {
            // Clean up very old verification records using configured retention period
            LocalDateTime verificationCutoff = LocalDateTime.now().minusDays(maintenanceConfig.getVerificationRetentionDays());
            long oldRecordsCountBefore = emailVerificationRepository.count();
            emailVerificationRepository.deleteExpiredVerifications(verificationCutoff);
            long oldRecordsCountAfter = emailVerificationRepository.count();
            int deletedRecords = (int) (oldRecordsCountBefore - oldRecordsCountAfter);

            logger.info("Database optimization completed. Deleted {} old verification records", deletedRecords);

            long executionTime = System.currentTimeMillis() - startTime;

            // Log maintenance event
            securityAuditService.logMaintenanceEvent("DATABASE_OPTIMIZATION", deletedRecords,
                "Deleted verification records older than " + maintenanceConfig.getVerificationRetentionDays() + " days");

            // Log performance metrics
            securityAuditService.logPerformanceMetrics("DATABASE_OPTIMIZATION", executionTime, deletedRecords);

        } catch (Exception e) {
            logger.error("Error during database optimization: {}", e.getMessage(), e);
            
            // Log maintenance failure
            securityAuditService.logMaintenanceEvent("DATABASE_OPTIMIZATION", 0,
                "Optimization failed: " + e.getMessage());
        }
    }

    /**
     * Manual trigger for all maintenance tasks.
     * Can be called on-demand for immediate maintenance.
     */
    @Transactional
    public void performAllMaintenanceTasks() {
        logger.info("Performing all maintenance tasks manually");
        
        try {
            // File sharing maintenance tasks
            cleanupExpiredShares();
            generateShareAnalytics();
            monitorSharingSystemHealth();
            
            // Existing maintenance tasks
            cleanupPendingAccounts();
            generateHealthMetrics();
            performDatabaseOptimization();
            
            logger.info("All maintenance tasks completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during manual maintenance tasks: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Manual trigger for file sharing maintenance tasks only.
     * Can be called on-demand for immediate file sharing system maintenance.
     */
    @Transactional
    public void performSharingMaintenanceTasks() {
        logger.info("Performing file sharing maintenance tasks manually");
        
        try {
            cleanupExpiredShares();
            generateShareAnalytics();
            monitorSharingSystemHealth();
            
            logger.info("File sharing maintenance tasks completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during manual sharing maintenance tasks: {}", e.getMessage(), e);
        }
    }
}