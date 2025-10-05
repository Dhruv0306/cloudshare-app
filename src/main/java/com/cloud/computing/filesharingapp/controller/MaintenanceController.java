package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.service.EmailVerificationService;
import com.cloud.computing.filesharingapp.service.MaintenanceTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for maintenance and administrative operations.
 * Provides endpoints for manual cleanup and system health monitoring.
 */
@RestController
@RequestMapping("/api/admin/maintenance")
@PreAuthorize("hasRole('ADMIN')") // Only admin users can access these endpoints
public class MaintenanceController {

    private static final Logger logger = LoggerFactory.getLogger(MaintenanceController.class);

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private MaintenanceTaskService maintenanceTaskService;

    /**
     * Manually trigger cleanup of expired verification codes.
     * 
     * @return Response indicating the result of the cleanup operation
     */
    @PostMapping("/cleanup-verifications")
    public ResponseEntity<Map<String, Object>> cleanupVerifications() {
        logger.info("Manual cleanup of verification codes requested");
        
        try {
            emailVerificationService.performManualCleanup();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Verification codes cleanup completed successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during manual verification cleanup: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Cleanup failed: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Manually trigger all maintenance tasks.
     * 
     * @return Response indicating the result of the maintenance operations
     */
    @PostMapping("/run-all-tasks")
    public ResponseEntity<Map<String, Object>> runAllMaintenanceTasks() {
        logger.info("Manual execution of all maintenance tasks requested");
        
        try {
            maintenanceTaskService.performAllMaintenanceTasks();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All maintenance tasks completed successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during manual maintenance tasks: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Maintenance tasks failed: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get system health and verification statistics.
     * 
     * @return System health metrics and statistics
     */
    @GetMapping("/health-stats")
    public ResponseEntity<Map<String, Object>> getHealthStats() {
        logger.info("System health statistics requested");
        
        try {
            EmailVerificationService.VerificationStats stats = emailVerificationService.getVerificationStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            
            Map<String, Object> verificationStats = new HashMap<>();
            verificationStats.put("totalVerifications", stats.getTotalVerifications());
            verificationStats.put("expiredCodes", stats.getExpiredCodes());
            
            response.put("verificationStats", verificationStats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving health statistics: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve health statistics: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Manually trigger cleanup of pending user accounts.
     * 
     * @return Response indicating the result of the cleanup operation
     */
    @PostMapping("/cleanup-pending-accounts")
    public ResponseEntity<Map<String, Object>> cleanupPendingAccounts() {
        logger.info("Manual cleanup of pending accounts requested");
        
        try {
            maintenanceTaskService.cleanupPendingAccounts();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Pending accounts cleanup completed successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during manual pending accounts cleanup: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Pending accounts cleanup failed: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get detailed system information for monitoring purposes.
     * 
     * @return Detailed system information and metrics
     */
    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        logger.info("System information requested");
        
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            
            // JVM information
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvmInfo = new HashMap<>();
            jvmInfo.put("totalMemory", runtime.totalMemory());
            jvmInfo.put("freeMemory", runtime.freeMemory());
            jvmInfo.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            jvmInfo.put("maxMemory", runtime.maxMemory());
            jvmInfo.put("availableProcessors", runtime.availableProcessors());
            
            response.put("jvmInfo", jvmInfo);
            
            // Application information
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("applicationName", "File Sharing App");
            appInfo.put("version", "1.0.0");
            appInfo.put("profile", "default");
            
            response.put("applicationInfo", appInfo);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving system information: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve system information: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}