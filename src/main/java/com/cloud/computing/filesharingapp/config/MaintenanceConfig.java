package com.cloud.computing.filesharingapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for automated maintenance tasks.
 * 
 * <p>This configuration class provides centralized management of maintenance
 * task settings including cleanup intervals, retention periods, and performance
 * thresholds for the file sharing system.
 * 
 * <p>Properties can be configured in application.properties or application.yml
 * using the "app.maintenance" prefix.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "app.maintenance")
public class MaintenanceConfig {

    /** Number of days to retain access logs before cleanup (default: 90 days) */
    private int accessLogRetentionDays = 90;
    
    /** Number of days to retain old verification records (default: 30 days) */
    private int verificationRetentionDays = 30;
    
    /** Maximum acceptable database query time in milliseconds (default: 1000ms) */
    private long maxQueryTimeMs = 1000;
    
    /** Maximum acceptable access rate per hour before flagging as suspicious (default: 1000) */
    private long maxAccessRatePerHour = 1000;
    
    /** Minimum number of accesses from an IP to be considered suspicious (default: 50) */
    private int suspiciousAccessThreshold = 50;
    
    /** Maximum number of suspicious IPs before system health warning (default: 5) */
    private int maxSuspiciousIps = 5;
    
    /** Maximum number of log files to keep during cleanup (default: 10) */
    private int maxLogFiles = 10;
    
    /** Enable or disable automated cleanup tasks (default: true) */
    private boolean cleanupEnabled = true;
    
    /** Enable or disable analytics generation (default: true) */
    private boolean analyticsEnabled = true;
    
    /** Enable or disable health monitoring (default: true) */
    private boolean healthMonitoringEnabled = true;

    /**
     * Gets the number of days to retain access logs.
     * 
     * @return access log retention period in days
     */
    public int getAccessLogRetentionDays() {
        return accessLogRetentionDays;
    }

    /**
     * Sets the number of days to retain access logs.
     * 
     * @param accessLogRetentionDays retention period in days
     */
    public void setAccessLogRetentionDays(int accessLogRetentionDays) {
        this.accessLogRetentionDays = accessLogRetentionDays;
    }

    /**
     * Gets the number of days to retain verification records.
     * 
     * @return verification record retention period in days
     */
    public int getVerificationRetentionDays() {
        return verificationRetentionDays;
    }

    /**
     * Sets the number of days to retain verification records.
     * 
     * @param verificationRetentionDays retention period in days
     */
    public void setVerificationRetentionDays(int verificationRetentionDays) {
        this.verificationRetentionDays = verificationRetentionDays;
    }

    /**
     * Gets the maximum acceptable database query time.
     * 
     * @return maximum query time in milliseconds
     */
    public long getMaxQueryTimeMs() {
        return maxQueryTimeMs;
    }

    /**
     * Sets the maximum acceptable database query time.
     * 
     * @param maxQueryTimeMs maximum query time in milliseconds
     */
    public void setMaxQueryTimeMs(long maxQueryTimeMs) {
        this.maxQueryTimeMs = maxQueryTimeMs;
    }

    /**
     * Gets the maximum acceptable access rate per hour.
     * 
     * @return maximum access rate per hour
     */
    public long getMaxAccessRatePerHour() {
        return maxAccessRatePerHour;
    }

    /**
     * Sets the maximum acceptable access rate per hour.
     * 
     * @param maxAccessRatePerHour maximum access rate per hour
     */
    public void setMaxAccessRatePerHour(long maxAccessRatePerHour) {
        this.maxAccessRatePerHour = maxAccessRatePerHour;
    }

    /**
     * Gets the threshold for suspicious access detection.
     * 
     * @return suspicious access threshold
     */
    public int getSuspiciousAccessThreshold() {
        return suspiciousAccessThreshold;
    }

    /**
     * Sets the threshold for suspicious access detection.
     * 
     * @param suspiciousAccessThreshold suspicious access threshold
     */
    public void setSuspiciousAccessThreshold(int suspiciousAccessThreshold) {
        this.suspiciousAccessThreshold = suspiciousAccessThreshold;
    }

    /**
     * Gets the maximum number of suspicious IPs before health warning.
     * 
     * @return maximum suspicious IPs threshold
     */
    public int getMaxSuspiciousIps() {
        return maxSuspiciousIps;
    }

    /**
     * Sets the maximum number of suspicious IPs before health warning.
     * 
     * @param maxSuspiciousIps maximum suspicious IPs threshold
     */
    public void setMaxSuspiciousIps(int maxSuspiciousIps) {
        this.maxSuspiciousIps = maxSuspiciousIps;
    }

    /**
     * Gets the maximum number of log files to keep.
     * 
     * @return maximum log files count
     */
    public int getMaxLogFiles() {
        return maxLogFiles;
    }

    /**
     * Sets the maximum number of log files to keep.
     * 
     * @param maxLogFiles maximum log files count
     */
    public void setMaxLogFiles(int maxLogFiles) {
        this.maxLogFiles = maxLogFiles;
    }

    /**
     * Checks if automated cleanup is enabled.
     * 
     * @return true if cleanup is enabled
     */
    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    /**
     * Enables or disables automated cleanup.
     * 
     * @param cleanupEnabled cleanup enabled flag
     */
    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    /**
     * Checks if analytics generation is enabled.
     * 
     * @return true if analytics is enabled
     */
    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    /**
     * Enables or disables analytics generation.
     * 
     * @param analyticsEnabled analytics enabled flag
     */
    public void setAnalyticsEnabled(boolean analyticsEnabled) {
        this.analyticsEnabled = analyticsEnabled;
    }

    /**
     * Checks if health monitoring is enabled.
     * 
     * @return true if health monitoring is enabled
     */
    public boolean isHealthMonitoringEnabled() {
        return healthMonitoringEnabled;
    }

    /**
     * Enables or disables health monitoring.
     * 
     * @param healthMonitoringEnabled health monitoring enabled flag
     */
    public void setHealthMonitoringEnabled(boolean healthMonitoringEnabled) {
        this.healthMonitoringEnabled = healthMonitoringEnabled;
    }
}