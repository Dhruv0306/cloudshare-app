package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.service.AdvancedSecurityService;
import com.cloud.computing.filesharingapp.service.RateLimitingService;
import com.cloud.computing.filesharingapp.service.SecurityMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for security monitoring and analytics endpoints.
 * 
 * <p>This controller provides comprehensive security monitoring capabilities including:
 * <ul>
 *   <li>Security dashboard with real-time metrics</li>
 *   <li>Threat level assessment and monitoring</li>
 *   <li>Rate limiting status and analytics</li>
 *   <li>Security incident reporting</li>
 *   <li>Anomaly detection results</li>
 *   <li>Administrative security controls</li>
 * </ul>
 * 
 * <p>All endpoints require administrative privileges and are protected
 * by Spring Security authorization checks.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/security")
@PreAuthorize("hasRole('ADMIN')")
public class SecurityController {

    private static final Logger logger = LoggerFactory.getLogger(SecurityController.class);

    @Autowired
    private SecurityMonitoringService securityMonitoringService;

    @Autowired
    private AdvancedSecurityService advancedSecurityService;

    @Autowired
    private RateLimitingService rateLimitingService;

    /**
     * Gets the comprehensive security dashboard with real-time metrics.
     * 
     * <p>The dashboard provides an overview of the current security state including:
     * <ul>
     *   <li>Security analytics and threat metrics</li>
     *   <li>Rate limiting statistics</li>
     *   <li>Detected anomalies and security events</li>
     *   <li>Access pattern analysis</li>
     *   <li>Geographic distribution of access attempts</li>
     *   <li>Overall security score</li>
     * </ul>
     * 
     * @param timeWindow the time window for analysis in hours (default: 24)
     * @return ResponseEntity containing the security dashboard data
     */
    @GetMapping("/dashboard")
    public ResponseEntity<SecurityMonitoringService.SecurityDashboard> getSecurityDashboard(
            @RequestParam(defaultValue = "24") int timeWindow) {
        
        logger.info("Generating security dashboard for {} hour window", timeWindow);

        try {
            SecurityMonitoringService.SecurityDashboard dashboard = 
                securityMonitoringService.generateSecurityDashboard(timeWindow);

            logger.debug("Security dashboard generated successfully - score: {}, anomalies: {}, events: {}", 
                        dashboard.getSecurityScore(), dashboard.getAnomalies().size(), dashboard.getTopEvents().size());

            return ResponseEntity.ok(dashboard);

        } catch (Exception e) {
            logger.error("Error generating security dashboard", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gets detailed security analytics for a specific time period.
     * 
     * @param timeWindow the time window for analysis in hours (default: 24)
     * @return ResponseEntity containing security analytics data
     */
    @GetMapping("/analytics")
    public ResponseEntity<AdvancedSecurityService.SecurityAnalytics> getSecurityAnalytics(
            @RequestParam(defaultValue = "24") int timeWindow) {
        
        logger.info("Retrieving security analytics for {} hour window", timeWindow);

        try {
            AdvancedSecurityService.SecurityAnalytics analytics = 
                advancedSecurityService.getSecurityAnalytics(timeWindow);

            return ResponseEntity.ok(analytics);

        } catch (Exception e) {
            logger.error("Error retrieving security analytics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gets rate limiting analytics and statistics.
     * 
     * @param timeWindow the time window for analysis in hours (default: 24)
     * @return ResponseEntity containing rate limiting analytics
     */
    @GetMapping("/rate-limiting")
    public ResponseEntity<RateLimitingService.RateLimitAnalytics> getRateLimitingAnalytics(
            @RequestParam(defaultValue = "24") int timeWindow) {
        
        logger.info("Retrieving rate limiting analytics for {} hour window", timeWindow);

        try {
            RateLimitingService.RateLimitAnalytics analytics = 
                rateLimitingService.getAnalytics(timeWindow);

            return ResponseEntity.ok(analytics);

        } catch (Exception e) {
            logger.error("Error retrieving rate limiting analytics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gets the current threat level for a specific IP address.
     * 
     * @param ipAddress the IP address to assess
     * @return ResponseEntity containing the threat level assessment
     */
    @GetMapping("/threat-level/{ipAddress}")
    public ResponseEntity<Map<String, Object>> getThreatLevel(@PathVariable String ipAddress) {
        
        logger.info("Assessing threat level for IP: {}", ipAddress);

        try {
            AdvancedSecurityService.SecurityThreatLevel threatLevel = 
                advancedSecurityService.getThreatLevel(ipAddress);

            Map<String, Object> response = new HashMap<>();
            response.put("ipAddress", ipAddress);
            response.put("threatLevel", threatLevel);
            response.put("assessedAt", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error assessing threat level for IP: {}", ipAddress, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gets rate limiting status for a specific IP address.
     * 
     * @param ipAddress the IP address to check
     * @return ResponseEntity containing rate limiting status
     */
    @GetMapping("/rate-limit-status/{ipAddress}")
    public ResponseEntity<RateLimitingService.RateLimitStatus> getRateLimitStatus(@PathVariable String ipAddress) {
        
        logger.info("Checking rate limit status for IP: {}", ipAddress);

        try {
            RateLimitingService.RateLimitStatus status = 
                rateLimitingService.getRateLimitStatus(ipAddress);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error checking rate limit status for IP: {}", ipAddress, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Detects security anomalies for a specific time period.
     * 
     * @param timeWindow the time window for analysis in hours (default: 24)
     * @return ResponseEntity containing detected anomalies
     */
    @GetMapping("/anomalies")
    public ResponseEntity<Map<String, Object>> getSecurityAnomalies(
            @RequestParam(defaultValue = "24") int timeWindow) {
        
        logger.info("Detecting security anomalies for {} hour window", timeWindow);

        try {
            LocalDateTime since = LocalDateTime.now().minusHours(timeWindow);
            LocalDateTime until = LocalDateTime.now();

            var anomalies = securityMonitoringService.detectAnomalies(since, until);

            Map<String, Object> response = new HashMap<>();
            response.put("anomalies", anomalies);
            response.put("count", anomalies.size());
            response.put("periodStart", since);
            response.put("periodEnd", until);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error detecting security anomalies", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generates a comprehensive security incident report.
     * 
     * @param timeWindow the time window for the report in hours (default: 24)
     * @return ResponseEntity containing the incident report
     */
    @GetMapping("/incident-report")
    public ResponseEntity<SecurityMonitoringService.SecurityIncidentReport> getIncidentReport(
            @RequestParam(defaultValue = "24") int timeWindow) {
        
        logger.info("Generating security incident report for {} hour window", timeWindow);

        try {
            LocalDateTime since = LocalDateTime.now().minusHours(timeWindow);
            LocalDateTime until = LocalDateTime.now();

            SecurityMonitoringService.SecurityIncidentReport report = 
                securityMonitoringService.generateIncidentReport(since, until);

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            logger.error("Error generating security incident report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually blacklists an IP address for security purposes.
     * 
     * @param ipAddress the IP address to blacklist
     * @param request the HTTP request for audit logging
     * @return ResponseEntity indicating the result of the operation
     */
    @PostMapping("/blacklist/{ipAddress}")
    public ResponseEntity<Map<String, Object>> blacklistIp(
            @PathVariable String ipAddress,
            @RequestParam(defaultValue = "24") int durationHours,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {
        
        logger.warn("Manual IP blacklist request for: {} duration: {} hours, reason: {}", 
                   ipAddress, durationHours, reason);

        try {
            String actualReason = reason != null ? reason : "Manual blacklist via admin interface";
            advancedSecurityService.blacklistIp(ipAddress, durationHours, actualReason);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ipAddress", ipAddress);
            response.put("durationHours", durationHours);
            response.put("reason", actualReason);
            response.put("blacklistedAt", LocalDateTime.now());

            logger.info("IP address blacklisted successfully: {}", ipAddress);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error blacklisting IP address: {}", ipAddress, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to blacklist IP address");
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Removes an IP address from the blacklist.
     * 
     * @param ipAddress the IP address to unblacklist
     * @param request the HTTP request for audit logging
     * @return ResponseEntity indicating the result of the operation
     */
    @DeleteMapping("/blacklist/{ipAddress}")
    public ResponseEntity<Map<String, Object>> unblacklistIp(
            @PathVariable String ipAddress,
            HttpServletRequest request) {
        
        logger.info("Manual IP unblacklist request for: {}", ipAddress);

        try {
            advancedSecurityService.unblacklistIp(ipAddress);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ipAddress", ipAddress);
            response.put("unblacklistedAt", LocalDateTime.now());

            logger.info("IP address unblacklisted successfully: {}", ipAddress);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error unblacklisting IP address: {}", ipAddress, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to unblacklist IP address");
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Triggers automated security response for a specific threat level and IP.
     * 
     * @param threatLevel the threat level to respond to
     * @param ipAddress the IP address (optional)
     * @return ResponseEntity containing the security response details
     */
    @PostMapping("/automated-response")
    public ResponseEntity<SecurityMonitoringService.SecurityResponse> triggerAutomatedResponse(
            @RequestParam AdvancedSecurityService.SecurityThreatLevel threatLevel,
            @RequestParam(required = false) String ipAddress) {
        
        logger.info("Triggering automated security response for threat level: {} IP: {}", threatLevel, ipAddress);

        try {
            SecurityMonitoringService.SecurityResponse response = 
                securityMonitoringService.performAutomatedResponse(threatLevel, ipAddress);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error triggering automated security response", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clears all security state (for testing or maintenance purposes).
     * 
     * @return ResponseEntity indicating the result of the operation
     */
    @PostMapping("/clear-state")
    public ResponseEntity<Map<String, Object>> clearSecurityState() {
        
        logger.warn("Clearing all security state - this should only be done for testing or maintenance");

        try {
            advancedSecurityService.clearSecurityState();
            rateLimitingService.clearRateLimitState();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Security state cleared successfully");
            response.put("clearedAt", LocalDateTime.now());

            logger.info("Security state cleared successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error clearing security state", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to clear security state");
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Gets system health status related to security monitoring.
     * 
     * @return ResponseEntity containing system health information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSecurityHealth() {
        
        logger.debug("Checking security system health");

        try {
            Map<String, Object> health = new HashMap<>();
            
            // Get basic metrics
            AdvancedSecurityService.SecurityAnalytics analytics = 
                advancedSecurityService.getSecurityAnalytics(1); // Last hour
            
            RateLimitingService.RateLimitAnalytics rateLimitAnalytics = 
                rateLimitingService.getAnalytics(1); // Last hour
            
            // Calculate health indicators
            boolean isHealthy = analytics.getBlacklistedIps() < 10 && 
                               analytics.getHighThreatIps() < 5 &&
                               rateLimitAnalytics.getRateLimitedRequests() < 100;
            
            health.put("status", isHealthy ? "HEALTHY" : "WARNING");
            health.put("blacklistedIps", analytics.getBlacklistedIps());
            health.put("highThreatIps", analytics.getHighThreatIps());
            health.put("rateLimitedRequests", rateLimitAnalytics.getRateLimitedRequests());
            health.put("checkedAt", LocalDateTime.now());

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            logger.error("Error checking security health", e);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "ERROR");
            health.put("error", "Failed to check security health");
            
            return ResponseEntity.internalServerError().body(health);
        }
    }
}