package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.ShareAnalyticsDTO;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for generating comprehensive file sharing analytics and reporting.
 * 
 * <p>This service provides detailed analytics about the file sharing system including:
 * <ul>
 *   <li>Usage statistics and trends</li>
 *   <li>Performance metrics and health indicators</li>
 *   <li>Security monitoring and abuse detection</li>
 *   <li>System capacity and optimization insights</li>
 * </ul>
 * 
 * <p>The analytics are used by maintenance tasks, monitoring systems, and
 * administrative dashboards to provide insights into system usage and health.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class ShareAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(ShareAnalyticsService.class);

    @Autowired
    private FileShareRepository fileShareRepository;

    @Autowired
    private ShareAccessRepository shareAccessRepository;

    /**
     * Generates comprehensive analytics for the file sharing system.
     * 
     * <p>This method collects and analyzes various metrics including:
     * <ul>
     *   <li>Share creation and activity statistics</li>
     *   <li>Access patterns and usage trends</li>
     *   <li>Performance and health indicators</li>
     *   <li>Security and abuse detection metrics</li>
     * </ul>
     * 
     * @return ShareAnalyticsDTO containing comprehensive analytics data
     */
    public ShareAnalyticsDTO generateComprehensiveAnalytics() {
        logger.debug("Generating comprehensive share analytics");
        
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime twentyFourHoursAgo = currentTime.minusHours(24);
            LocalDateTime sevenDaysAgo = currentTime.minusDays(7);

            ShareAnalyticsDTO analytics = new ShareAnalyticsDTO();
            analytics.setGeneratedAt(currentTime);

            // Collect basic share statistics
            analytics.setTotalShares(fileShareRepository.count());
            analytics.setActiveShares(fileShareRepository.countByActiveTrue());
            analytics.setExpiredShares(fileShareRepository.findExpiredActiveShares(currentTime).size());
            analytics.setMaxAccessReachedShares(fileShareRepository.findMaxAccessReachedShares().size());

            // Collect access statistics
            analytics.setTotalAccesses24h(shareAccessRepository.countByAccessedAtAfter(twentyFourHoursAgo));
            analytics.setViewAccesses24h(shareAccessRepository.countByAccessTypeAndAccessedAtAfter(
                ShareAccessType.VIEW, twentyFourHoursAgo));
            analytics.setDownloadAccesses24h(shareAccessRepository.countByAccessTypeAndAccessedAtAfter(
                ShareAccessType.DOWNLOAD, twentyFourHoursAgo));
            analytics.setTotalAccesses7d(shareAccessRepository.countByAccessedAtAfter(sevenDaysAgo));

            // Collect performance metrics
            long queryStartTime = System.currentTimeMillis();
            fileShareRepository.findByActiveTrueOrderByCreatedAtDesc(); // Performance test query
            analytics.setQueryPerformanceMs(System.currentTimeMillis() - queryStartTime);

            // Collect security metrics
            List<Object[]> suspiciousIPs = shareAccessRepository.findSuspiciousAccessPatterns(
                twentyFourHoursAgo, 50);
            analytics.setSuspiciousIpCount(suspiciousIPs.size());

            // Calculate derived metrics
            analytics.calculateDerivedMetrics();

            // Determine health status
            determineHealthStatus(analytics);

            logger.debug("Analytics generation completed successfully");
            return analytics;

        } catch (Exception e) {
            logger.error("Error generating comprehensive analytics: {}", e.getMessage(), e);
            
            // Return minimal analytics with error status
            ShareAnalyticsDTO errorAnalytics = new ShareAnalyticsDTO();
            errorAnalytics.setHealthStatus("ERROR");
            errorAnalytics.setHealthIssues("Analytics generation failed: " + e.getMessage());
            return errorAnalytics;
        }
    }

    /**
     * Generates analytics for a specific time period.
     * 
     * @param startDate the start of the analysis period
     * @param endDate the end of the analysis period
     * @return ShareAnalyticsDTO containing period-specific analytics
     */
    public ShareAnalyticsDTO generatePeriodAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        logger.debug("Generating analytics for period: {} to {}", startDate, endDate);
        
        try {
            ShareAnalyticsDTO analytics = new ShareAnalyticsDTO();
            analytics.setGeneratedAt(LocalDateTime.now());

            // Get shares created in the period
            long sharesInPeriod = fileShareRepository.countByCreatedAtAfter(startDate);
            analytics.setTotalShares(sharesInPeriod);

            // Get accesses in the period
            long accessesInPeriod = shareAccessRepository.countByAccessedAtAfter(startDate);
            analytics.setTotalAccesses24h(accessesInPeriod); // Reusing field for period data

            long viewAccessesInPeriod = shareAccessRepository.countByAccessTypeAndAccessedAtAfter(
                ShareAccessType.VIEW, startDate);
            analytics.setViewAccesses24h(viewAccessesInPeriod);

            long downloadAccessesInPeriod = shareAccessRepository.countByAccessTypeAndAccessedAtAfter(
                ShareAccessType.DOWNLOAD, startDate);
            analytics.setDownloadAccesses24h(downloadAccessesInPeriod);

            // Calculate derived metrics
            analytics.calculateDerivedMetrics();

            logger.debug("Period analytics generation completed");
            return analytics;

        } catch (Exception e) {
            logger.error("Error generating period analytics: {}", e.getMessage(), e);
            
            ShareAnalyticsDTO errorAnalytics = new ShareAnalyticsDTO();
            errorAnalytics.setHealthStatus("ERROR");
            errorAnalytics.setHealthIssues("Period analytics generation failed: " + e.getMessage());
            return errorAnalytics;
        }
    }

    /**
     * Gets quick health metrics for system monitoring.
     * 
     * @return ShareAnalyticsDTO with essential health indicators
     */
    public ShareAnalyticsDTO getQuickHealthMetrics() {
        logger.debug("Generating quick health metrics");
        
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime oneHourAgo = currentTime.minusHours(1);

            ShareAnalyticsDTO healthMetrics = new ShareAnalyticsDTO();
            healthMetrics.setGeneratedAt(currentTime);

            // Quick performance check
            long queryStartTime = System.currentTimeMillis();
            long activeShareCount = fileShareRepository.countByActiveTrue();
            healthMetrics.setQueryPerformanceMs(System.currentTimeMillis() - queryStartTime);
            healthMetrics.setActiveShares(activeShareCount);

            // Quick activity check
            long recentAccesses = shareAccessRepository.countByAccessedAtAfter(oneHourAgo);
            healthMetrics.setTotalAccesses24h(recentAccesses); // Reusing field for recent data

            // Quick security check
            List<Object[]> suspiciousActivity = shareAccessRepository.findSuspiciousAccessPatterns(
                oneHourAgo, 20);
            healthMetrics.setSuspiciousIpCount(suspiciousActivity.size());

            // Determine health status
            determineHealthStatus(healthMetrics);

            logger.debug("Quick health metrics generation completed");
            return healthMetrics;

        } catch (Exception e) {
            logger.error("Error generating quick health metrics: {}", e.getMessage(), e);
            
            ShareAnalyticsDTO errorMetrics = new ShareAnalyticsDTO();
            errorMetrics.setHealthStatus("CRITICAL");
            errorMetrics.setHealthIssues("Health check failed: " + e.getMessage());
            return errorMetrics;
        }
    }

    /**
     * Determines the overall health status based on analytics data.
     * 
     * @param analytics the analytics data to evaluate
     */
    private void determineHealthStatus(ShareAnalyticsDTO analytics) {
        StringBuilder healthIssues = new StringBuilder();
        boolean isHealthy = true;

        // Check query performance
        if (analytics.getQueryPerformanceMs() > 1000) {
            isHealthy = false;
            healthIssues.append("Slow database queries (")
                      .append(analytics.getQueryPerformanceMs())
                      .append("ms); ");
        }

        // Check for excessive suspicious activity
        if (analytics.getSuspiciousIpCount() > 5) {
            isHealthy = false;
            healthIssues.append("High suspicious activity (")
                      .append(analytics.getSuspiciousIpCount())
                      .append(" IPs); ");
        }

        // Check for excessive access rate (potential abuse)
        if (analytics.getTotalAccesses24h() > 10000) {
            isHealthy = false;
            healthIssues.append("Very high access rate (")
                      .append(analytics.getTotalAccesses24h())
                      .append("/24h); ");
        }

        // Set health status
        if (isHealthy) {
            analytics.setHealthStatus("HEALTHY");
            analytics.setHealthIssues("None");
        } else {
            // Determine severity
            if (analytics.getQueryPerformanceMs() > 5000 || analytics.getSuspiciousIpCount() > 20) {
                analytics.setHealthStatus("CRITICAL");
            } else {
                analytics.setHealthStatus("WARNING");
            }
            analytics.setHealthIssues(healthIssues.toString());
        }
    }

    /**
     * Logs analytics summary for monitoring and debugging.
     * 
     * @param analytics the analytics data to log
     */
    public void logAnalyticsSummary(ShareAnalyticsDTO analytics) {
        String summary = analytics.getAnalyticsSummary();
        
        if ("HEALTHY".equals(analytics.getHealthStatus())) {
            logger.info(summary);
        } else if ("WARNING".equals(analytics.getHealthStatus())) {
            logger.warn(summary + " - Issues: " + analytics.getHealthIssues());
        } else {
            logger.error(summary + " - Critical Issues: " + analytics.getHealthIssues());
        }
    }
}