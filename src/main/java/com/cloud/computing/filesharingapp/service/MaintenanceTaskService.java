package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.repository.EmailVerificationRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for handling scheduled maintenance tasks related to email verification
 * and user account management.
 */
@Service
public class MaintenanceTaskService {

    private static final Logger logger = LoggerFactory.getLogger(MaintenanceTaskService.class);

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityAuditService securityAuditService;

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
            // Clean up very old verification records (older than 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long oldRecordsCountBefore = emailVerificationRepository.count();
            emailVerificationRepository.deleteExpiredVerifications(thirtyDaysAgo);
            long oldRecordsCountAfter = emailVerificationRepository.count();
            int deletedRecords = (int) (oldRecordsCountBefore - oldRecordsCountAfter);

            logger.info("Database optimization completed. Deleted {} old verification records", deletedRecords);

            long executionTime = System.currentTimeMillis() - startTime;

            // Log maintenance event
            securityAuditService.logMaintenanceEvent("DATABASE_OPTIMIZATION", deletedRecords,
                "Deleted verification records older than 30 days");

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
            cleanupPendingAccounts();
            generateHealthMetrics();
            performDatabaseOptimization();
            
            logger.info("All maintenance tasks completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during manual maintenance tasks: {}", e.getMessage(), e);
        }
    }
}