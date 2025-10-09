package com.cloud.computing.filesharingapp.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for file sharing analytics and reporting.
 * 
 * <p>This DTO encapsulates comprehensive analytics data about the file sharing system
 * including usage statistics, performance metrics, and health indicators.
 * 
 * <p>Used by maintenance tasks and reporting services to provide insights into:
 * <ul>
 *   <li>Share creation and usage patterns</li>
 *   <li>Access statistics and download rates</li>
 *   <li>System performance and health metrics</li>
 *   <li>Security and abuse detection indicators</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class ShareAnalyticsDTO {
    
    /** Timestamp when the analytics were generated */
    private LocalDateTime generatedAt;
    
    /** Total number of shares in the system */
    private long totalShares;
    
    /** Number of currently active shares */
    private long activeShares;
    
    /** Number of expired shares */
    private long expiredShares;
    
    /** Number of shares that reached maximum access count */
    private long maxAccessReachedShares;
    
    /** Total access attempts in the last 24 hours */
    private long totalAccesses24h;
    
    /** View-only accesses in the last 24 hours */
    private long viewAccesses24h;
    
    /** Download accesses in the last 24 hours */
    private long downloadAccesses24h;
    
    /** Total access attempts in the last 7 days */
    private long totalAccesses7d;
    
    /** Percentage of shares that are currently active */
    private double activeSharePercentage;
    
    /** Download rate as percentage of total accesses in 24h */
    private double downloadRate24h;
    
    /** Number of suspicious IP addresses detected */
    private int suspiciousIpCount;
    
    /** Database query performance in milliseconds */
    private long queryPerformanceMs;
    
    /** Overall system health status */
    private String healthStatus;
    
    /** Detailed health issues if any */
    private String healthIssues;

    /**
     * Default constructor that initializes the generation timestamp.
     */
    public ShareAnalyticsDTO() {
        this.generatedAt = LocalDateTime.now();
    }

    /**
     * Constructor with basic analytics data.
     * 
     * @param totalShares total number of shares
     * @param activeShares number of active shares
     * @param totalAccesses24h total accesses in 24 hours
     */
    public ShareAnalyticsDTO(long totalShares, long activeShares, long totalAccesses24h) {
        this();
        this.totalShares = totalShares;
        this.activeShares = activeShares;
        this.totalAccesses24h = totalAccesses24h;
        calculateDerivedMetrics();
    }

    /**
     * Calculates derived metrics based on the base data.
     * 
     * <p>This method computes percentage values and rates from the raw counts
     * to provide more meaningful analytics insights.
     */
    public void calculateDerivedMetrics() {
        // Calculate active share percentage
        this.activeSharePercentage = totalShares > 0 ? (double) activeShares / totalShares * 100 : 0;
        
        // Calculate download rate
        this.downloadRate24h = totalAccesses24h > 0 ? (double) downloadAccesses24h / totalAccesses24h * 100 : 0;
    }

    /**
     * Generates a formatted analytics summary string.
     * 
     * @return formatted string containing key analytics metrics
     */
    public String getAnalyticsSummary() {
        return String.format(
            "Share Analytics Summary: Total: %d, Active: %d (%.1f%%), " +
            "24h Activity: %d accesses (%.1f%% downloads), Health: %s",
            totalShares, activeShares, activeSharePercentage,
            totalAccesses24h, downloadRate24h, healthStatus != null ? healthStatus : "Unknown"
        );
    }

    /**
     * Checks if the system is performing well based on key metrics.
     * 
     * @return true if all key performance indicators are within acceptable ranges
     */
    public boolean isPerformingWell() {
        return queryPerformanceMs < 1000 && 
               suspiciousIpCount < 5 && 
               "HEALTHY".equals(healthStatus);
    }

    // Getters and Setters
    
    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public long getTotalShares() {
        return totalShares;
    }

    public void setTotalShares(long totalShares) {
        this.totalShares = totalShares;
    }

    public long getActiveShares() {
        return activeShares;
    }

    public void setActiveShares(long activeShares) {
        this.activeShares = activeShares;
    }

    public long getExpiredShares() {
        return expiredShares;
    }

    public void setExpiredShares(long expiredShares) {
        this.expiredShares = expiredShares;
    }

    public long getMaxAccessReachedShares() {
        return maxAccessReachedShares;
    }

    public void setMaxAccessReachedShares(long maxAccessReachedShares) {
        this.maxAccessReachedShares = maxAccessReachedShares;
    }

    public long getTotalAccesses24h() {
        return totalAccesses24h;
    }

    public void setTotalAccesses24h(long totalAccesses24h) {
        this.totalAccesses24h = totalAccesses24h;
    }

    public long getViewAccesses24h() {
        return viewAccesses24h;
    }

    public void setViewAccesses24h(long viewAccesses24h) {
        this.viewAccesses24h = viewAccesses24h;
    }

    public long getDownloadAccesses24h() {
        return downloadAccesses24h;
    }

    public void setDownloadAccesses24h(long downloadAccesses24h) {
        this.downloadAccesses24h = downloadAccesses24h;
    }

    public long getTotalAccesses7d() {
        return totalAccesses7d;
    }

    public void setTotalAccesses7d(long totalAccesses7d) {
        this.totalAccesses7d = totalAccesses7d;
    }

    public double getActiveSharePercentage() {
        return activeSharePercentage;
    }

    public void setActiveSharePercentage(double activeSharePercentage) {
        this.activeSharePercentage = activeSharePercentage;
    }

    public double getDownloadRate24h() {
        return downloadRate24h;
    }

    public void setDownloadRate24h(double downloadRate24h) {
        this.downloadRate24h = downloadRate24h;
    }

    public int getSuspiciousIpCount() {
        return suspiciousIpCount;
    }

    public void setSuspiciousIpCount(int suspiciousIpCount) {
        this.suspiciousIpCount = suspiciousIpCount;
    }

    public long getQueryPerformanceMs() {
        return queryPerformanceMs;
    }

    public void setQueryPerformanceMs(long queryPerformanceMs) {
        this.queryPerformanceMs = queryPerformanceMs;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public String getHealthIssues() {
        return healthIssues;
    }

    public void setHealthIssues(String healthIssues) {
        this.healthIssues = healthIssues;
    }
}