package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.dto.ShareAnalyticsDTO;
import com.cloud.computing.filesharingapp.service.MaintenanceTaskService;
import com.cloud.computing.filesharingapp.service.ShareAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for maintenance and analytics operations.
 * 
 * <p>This controller provides endpoints for administrators to manually trigger
 * maintenance tasks and retrieve system analytics. All endpoints require
 * administrative privileges for security.
 * 
 * <p>Available operations:
 * <ul>
 *   <li>Manual maintenance task execution</li>
 *   <li>System analytics retrieval</li>
 *   <li>Health status monitoring</li>
 *   <li>Performance metrics access</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/admin/maintenance")
@PreAuthorize("hasRole('ADMIN')")
public class MaintenanceController {

    private static final Logger logger = LoggerFactory.getLogger(MaintenanceController.class);

    @Autowired
    private MaintenanceTaskService maintenanceTaskService;

    @Autowired
    private ShareAnalyticsService shareAnalyticsService;

    /**
     * Manually triggers all maintenance tasks.
     * 
     * <p>This endpoint allows administrators to run all scheduled maintenance
     * tasks on-demand for immediate system cleanup and optimization.
     * 
     * @return ResponseEntity with execution status and summary
     */
    @PostMapping("/run-all")
    public ResponseEntity<Map<String, Object>> runAllMaintenanceTasks() {
        logger.info("Manual execution of all maintenance tasks requested");
        
        try {
            long startTime = System.currentTimeMillis();
            maintenanceTaskService.performAllMaintenanceTasks();
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "All maintenance tasks completed successfully");
            response.put("executionTimeMs", executionTime);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Manual maintenance tasks completed in {}ms", executionTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during manual maintenance execution: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Maintenance tasks failed: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Manually triggers file sharing specific maintenance tasks.
     * 
     * @return ResponseEntity with execution status and summary
     */
    @PostMapping("/run-sharing")
    public ResponseEntity<Map<String, Object>> runSharingMaintenanceTasks() {
        logger.info("Manual execution of sharing maintenance tasks requested");
        
        try {
            long startTime = System.currentTimeMillis();
            maintenanceTaskService.performSharingMaintenanceTasks();
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Sharing maintenance tasks completed successfully");
            response.put("executionTimeMs", executionTime);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Manual sharing maintenance tasks completed in {}ms", executionTime);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during manual sharing maintenance execution: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Sharing maintenance tasks failed: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Retrieves comprehensive system analytics.
     * 
     * @return ResponseEntity containing detailed analytics data
     */
    @GetMapping("/analytics")
    public ResponseEntity<ShareAnalyticsDTO> getSystemAnalytics() {
        logger.debug("System analytics requested");
        
        try {
            ShareAnalyticsDTO analytics = shareAnalyticsService.generateComprehensiveAnalytics();
            logger.debug("System analytics generated successfully");
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            logger.error("Error generating system analytics: {}", e.getMessage(), e);
            
            // Return minimal error analytics
            ShareAnalyticsDTO errorAnalytics = new ShareAnalyticsDTO();
            errorAnalytics.setHealthStatus("ERROR");
            errorAnalytics.setHealthIssues("Analytics generation failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorAnalytics);
        }
    }

    /**
     * Retrieves quick health metrics for monitoring.
     * 
     * @return ResponseEntity containing health status and key metrics
     */
    @GetMapping("/health")
    public ResponseEntity<ShareAnalyticsDTO> getHealthMetrics() {
        logger.debug("Health metrics requested");
        
        try {
            ShareAnalyticsDTO healthMetrics = shareAnalyticsService.getQuickHealthMetrics();
            logger.debug("Health metrics generated successfully");
            return ResponseEntity.ok(healthMetrics);
            
        } catch (Exception e) {
            logger.error("Error generating health metrics: {}", e.getMessage(), e);
            
            // Return critical health status
            ShareAnalyticsDTO criticalHealth = new ShareAnalyticsDTO();
            criticalHealth.setHealthStatus("CRITICAL");
            criticalHealth.setHealthIssues("Health check failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(criticalHealth);
        }
    }

    /**
     * Retrieves system status summary for dashboard display.
     * 
     * @return ResponseEntity containing system status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        logger.debug("System status requested");
        
        try {
            ShareAnalyticsDTO analytics = shareAnalyticsService.getQuickHealthMetrics();
            
            Map<String, Object> status = new HashMap<>();
            status.put("healthStatus", analytics.getHealthStatus());
            status.put("activeShares", analytics.getActiveShares());
            status.put("recentAccesses", analytics.getTotalAccesses24h());
            status.put("queryPerformanceMs", analytics.getQueryPerformanceMs());
            status.put("suspiciousIpCount", analytics.getSuspiciousIpCount());
            status.put("isPerformingWell", analytics.isPerformingWell());
            status.put("timestamp", System.currentTimeMillis());
            
            if (analytics.getHealthIssues() != null && !analytics.getHealthIssues().equals("None")) {
                status.put("healthIssues", analytics.getHealthIssues());
            }
            
            logger.debug("System status generated successfully");
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error generating system status: {}", e.getMessage(), e);
            
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("healthStatus", "ERROR");
            errorStatus.put("healthIssues", "Status check failed: " + e.getMessage());
            errorStatus.put("isPerformingWell", false);
            errorStatus.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorStatus);
        }
    }
}