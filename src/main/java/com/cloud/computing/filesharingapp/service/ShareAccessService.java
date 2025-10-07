package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccess;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class for managing share access logging and security controls.
 * 
 * <p>This service provides comprehensive access logging and security features including:
 * <ul>
 *   <li>Detailed access logging with IP and user agent tracking</li>
 *   <li>IP-based rate limiting to prevent abuse</li>
 *   <li>Access statistics tracking and analytics</li>
 *   <li>Suspicious activity detection and alerting</li>
 *   <li>Access validation with permission and security checks</li>
 * </ul>
 * 
 * <p>The service maintains in-memory rate limiting counters and provides
 * real-time security monitoring for all share access attempts.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Transactional
public class ShareAccessService {

    private static final Logger logger = LoggerFactory.getLogger(ShareAccessService.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");

    @Autowired
    private ShareAccessRepository shareAccessRepository;

    // Rate limiting configuration
    @Value("${app.sharing.max-access-per-ip-per-hour:100}")
    private int maxAccessPerIpPerHour;

    @Value("${app.sharing.max-access-per-share-per-ip-per-hour:20}")
    private int maxAccessPerSharePerIpPerHour;

    @Value("${app.sharing.suspicious-activity-threshold:50}")
    private int suspiciousActivityThreshold;

    @Value("${app.sharing.rate-limit-window-hours:1}")
    private int rateLimitWindowHours;

    // In-memory rate limiting cache (in production, consider using Redis)
    private final Map<String, RateLimitInfo> ipRateLimitCache = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> shareIpRateLimitCache = new ConcurrentHashMap<>();

    /**
     * Logs an access attempt to a shared file.
     * 
     * <p>This method records detailed information about each access including:
     * <ul>
     *   <li>IP address and user agent of the accessor</li>
     *   <li>Timestamp of the access attempt</li>
     *   <li>Type of access (view or download)</li>
     *   <li>Associated file share information</li>
     * </ul>
     * 
     * @param fileShare the file share being accessed
     * @param accessorIp the IP address of the accessor
     * @param userAgent the user agent string from the request
     * @param accessType the type of access (VIEW or DOWNLOAD)
     * @return the created ShareAccess log entry
     */
    public ShareAccess logAccess(FileShare fileShare, String accessorIp, String userAgent, ShareAccessType accessType) {
        logger.info("Logging {} access for share ID: {} from IP: {}", 
                   accessType, fileShare.getId(), accessorIp);

        ShareAccess shareAccess = new ShareAccess(fileShare, accessorIp, userAgent, accessType);
        ShareAccess savedAccess = shareAccessRepository.save(shareAccess);

        // Update rate limiting cache
        updateRateLimitCache(accessorIp, fileShare.getId());

        // Check for suspicious activity
        checkSuspiciousActivity(accessorIp, fileShare);

        logger.debug("Access logged successfully - ID: {}, share: {}, IP: {}, type: {}", 
                    savedAccess.getId(), fileShare.getId(), accessorIp, accessType);

        return savedAccess;
    }

    /**
     * Validates if access is allowed based on rate limiting and security checks.
     * 
     * <p>This method performs comprehensive access validation including:
     * <ul>
     *   <li>IP-based rate limiting checks</li>
     *   <li>Share-specific rate limiting</li>
     *   <li>Suspicious activity detection</li>
     *   <li>Access permission validation</li>
     * </ul>
     * 
     * @param fileShare the file share being accessed
     * @param accessorIp the IP address of the accessor
     * @param accessType the type of access requested
     * @return AccessValidationResult containing validation outcome and details
     */
    public AccessValidationResult validateAccess(FileShare fileShare, String accessorIp, ShareAccessType accessType) {
        logger.debug("Validating access for share ID: {} from IP: {} for {}", 
                    fileShare.getId(), accessorIp, accessType);

        // Check if share is valid and active
        if (!fileShare.isValid()) {
            logger.warn("Access denied - invalid share: ID: {}, active: {}, expired: {}", 
                       fileShare.getId(), fileShare.isActive(), 
                       fileShare.getExpiresAt() != null && LocalDateTime.now().isAfter(fileShare.getExpiresAt()));
            return AccessValidationResult.denied("Share is invalid, expired, or has reached access limit");
        }

        // Check download permission for download requests
        if (accessType == ShareAccessType.DOWNLOAD && !fileShare.getPermission().allowsDownload()) {
            logger.warn("Access denied - download not permitted for share ID: {}, permission: {}", 
                       fileShare.getId(), fileShare.getPermission());
            return AccessValidationResult.denied("Download permission not granted for this share");
        }

        // Check IP-based rate limiting
        if (isIpRateLimited(accessorIp)) {
            securityLogger.warn("Rate limit exceeded for IP: {} (global limit: {} per hour)", 
                               accessorIp, maxAccessPerIpPerHour);
            return AccessValidationResult.rateLimited("Too many access attempts from your IP address. Please try again later.");
        }

        // Check share-specific IP rate limiting
        if (isShareIpRateLimited(fileShare.getId(), accessorIp)) {
            securityLogger.warn("Share-specific rate limit exceeded for IP: {} on share ID: {} (limit: {} per hour)", 
                               accessorIp, fileShare.getId(), maxAccessPerSharePerIpPerHour);
            return AccessValidationResult.rateLimited("Too many access attempts for this file from your IP address. Please try again later.");
        }

        // Check for suspicious activity patterns
        if (isSuspiciousActivity(accessorIp, fileShare)) {
            securityLogger.warn("Suspicious activity detected for IP: {} on share ID: {}", 
                               accessorIp, fileShare.getId());
            return AccessValidationResult.suspicious("Suspicious activity detected. Access temporarily restricted.");
        }

        logger.debug("Access validation passed for share ID: {} from IP: {}", 
                    fileShare.getId(), accessorIp);
        return AccessValidationResult.allowed();
    }

    /**
     * Retrieves access history for a specific file share.
     * 
     * @param fileShare the file share to get history for
     * @return List of ShareAccess entries ordered by access time (newest first)
     */
    public List<ShareAccess> getShareAccessHistory(FileShare fileShare) {
        logger.debug("Retrieving access history for share ID: {}", fileShare.getId());
        return shareAccessRepository.findByFileShareOrderByAccessedAtDesc(fileShare);
    }

    /**
     * Retrieves access history for a specific file share and access type.
     * 
     * @param fileShare the file share to query
     * @param accessType the type of access to filter by
     * @return List of ShareAccess entries matching the criteria
     */
    public List<ShareAccess> getShareAccessHistory(FileShare fileShare, ShareAccessType accessType) {
        logger.debug("Retrieving {} access history for share ID: {}", accessType, fileShare.getId());
        return shareAccessRepository.findByFileShareAndAccessTypeOrderByAccessedAtDesc(fileShare, accessType);
    }

    /**
     * Gets comprehensive access statistics for a file share.
     * 
     * @param fileShare the file share to analyze
     * @return ShareAccessStats containing detailed statistics
     */
    public ShareAccessStats getAccessStatistics(FileShare fileShare) {
        logger.debug("Generating access statistics for share ID: {}", fileShare.getId());

        long totalAccesses = shareAccessRepository.countByFileShare(fileShare);
        long viewCount = shareAccessRepository.countByFileShareAndAccessType(fileShare, ShareAccessType.VIEW);
        long downloadCount = shareAccessRepository.countByFileShareAndAccessType(fileShare, ShareAccessType.DOWNLOAD);

        // Get recent activity (last 24 hours) - get all accesses and filter by date
        LocalDateTime since24Hours = LocalDateTime.now().minusHours(24);
        List<ShareAccess> allAccesses = shareAccessRepository.findByFileShareOrderByAccessedAtDesc(fileShare);
        long recentAccessCount = allAccesses.stream()
            .filter(access -> access.getAccessedAt().isAfter(since24Hours))
            .count();

        // Get access statistics by type for the last 7 days
        LocalDateTime since7Days = LocalDateTime.now().minusDays(7);
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> weeklyStats = shareAccessRepository.getAccessStatsByTypeAndPeriod(fileShare, since7Days, now);

        return new ShareAccessStats(
            totalAccesses,
            viewCount,
            downloadCount,
            recentAccessCount,
            weeklyStats
        );
    }

    /**
     * Detects and reports suspicious access patterns.
     * 
     * @param accessorIp the IP address to check (optional, null for all IPs)
     * @param timeWindowHours the time window to analyze (default: 1 hour)
     * @return List of suspicious activity reports
     */
    public List<SuspiciousActivityReport> detectSuspiciousActivity(String accessorIp, int timeWindowHours) {
        logger.info("Detecting suspicious activity for IP: {} in last {} hours", 
                   accessorIp != null ? accessorIp : "ALL", timeWindowHours);

        LocalDateTime since = LocalDateTime.now().minusHours(timeWindowHours);
        
        if (accessorIp != null) {
            // Check specific IP
            List<Object[]> patterns = shareAccessRepository.findSuspiciousAccessPatterns(
                accessorIp, since, suspiciousActivityThreshold);
            
            return patterns.stream()
                .map(pattern -> new SuspiciousActivityReport(
                    (String) pattern[0], 
                    (Long) pattern[1], 
                    since, 
                    LocalDateTime.now(),
                    "High access frequency detected"
                ))
                .toList();
        } else {
            // This would require a different query for all IPs - simplified for now
            return List.of();
        }
    }

    /**
     * Clears rate limiting cache for testing or administrative purposes.
     */
    public void clearRateLimitCache() {
        logger.info("Clearing rate limit cache");
        ipRateLimitCache.clear();
        shareIpRateLimitCache.clear();
    }

    /**
     * Gets current rate limit status for an IP address.
     * 
     * @param accessorIp the IP address to check
     * @return RateLimitStatus containing current limits and usage
     */
    public RateLimitStatus getRateLimitStatus(String accessorIp) {
        RateLimitInfo globalInfo = ipRateLimitCache.get(accessorIp);
        
        // Count actual accesses from database for accuracy
        LocalDateTime since = LocalDateTime.now().minusHours(rateLimitWindowHours);
        long actualCount = shareAccessRepository.countByAccessorIpAndAccessedAtAfter(accessorIp, since);
        
        return new RateLimitStatus(
            accessorIp,
            actualCount,
            maxAccessPerIpPerHour,
            globalInfo != null ? globalInfo.getWindowStart() : null,
            actualCount >= maxAccessPerIpPerHour
        );
    }

    // Private helper methods

    private boolean isIpRateLimited(String accessorIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(rateLimitWindowHours);
        
        // Check database for accurate count
        long accessCount = shareAccessRepository.countByAccessorIpAndAccessedAtAfter(accessorIp, windowStart);
        
        boolean isLimited = accessCount >= maxAccessPerIpPerHour;
        
        if (isLimited) {
            logger.warn("IP rate limit check: {} has {} accesses (limit: {})", 
                       accessorIp, accessCount, maxAccessPerIpPerHour);
        }
        
        return isLimited;
    }

    private boolean isShareIpRateLimited(Long shareId, String accessorIp) {
        // This would require a method in repository to count by share and IP
        // For now, using a simplified approach with cache
        String cacheKey = shareId + ":" + accessorIp;
        RateLimitInfo info = shareIpRateLimitCache.get(cacheKey);
        
        if (info == null || info.isExpired(rateLimitWindowHours)) {
            return false;
        }
        
        return info.getCount() >= maxAccessPerSharePerIpPerHour;
    }

    private boolean isSuspiciousActivity(String accessorIp, FileShare fileShare) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        
        List<Object[]> patterns = shareAccessRepository.findSuspiciousAccessPatterns(
            accessorIp, since, suspiciousActivityThreshold);
        
        return !patterns.isEmpty();
    }

    private void updateRateLimitCache(String accessorIp, Long shareId) {
        LocalDateTime now = LocalDateTime.now();
        
        // Update global IP rate limit
        ipRateLimitCache.compute(accessorIp, (ip, info) -> {
            if (info == null || info.isExpired(rateLimitWindowHours)) {
                return new RateLimitInfo(now, 1);
            } else {
                info.increment();
                return info;
            }
        });
        
        // Update share-specific IP rate limit
        String shareIpKey = shareId + ":" + accessorIp;
        shareIpRateLimitCache.compute(shareIpKey, (key, info) -> {
            if (info == null || info.isExpired(rateLimitWindowHours)) {
                return new RateLimitInfo(now, 1);
            } else {
                info.increment();
                return info;
            }
        });
    }

    private void checkSuspiciousActivity(String accessorIp, FileShare fileShare) {
        // Check if this IP has made too many requests recently
        LocalDateTime since = LocalDateTime.now().minusMinutes(10);
        long recentCount = shareAccessRepository.countByAccessorIpAndAccessedAtAfter(accessorIp, since);
        
        if (recentCount > 20) { // More than 20 requests in 10 minutes
            securityLogger.warn("Potential abuse detected: IP {} made {} requests in 10 minutes for share ID: {}", 
                               accessorIp, recentCount, fileShare.getId());
        }
    }

    // Inner classes for return types

    /**
     * Result of access validation containing outcome and details.
     */
    public static class AccessValidationResult {
        private final boolean allowed;
        private final String reason;
        private final AccessDenialType denialType;

        private AccessValidationResult(boolean allowed, String reason, AccessDenialType denialType) {
            this.allowed = allowed;
            this.reason = reason;
            this.denialType = denialType;
        }

        public static AccessValidationResult allowed() {
            return new AccessValidationResult(true, null, null);
        }

        public static AccessValidationResult denied(String reason) {
            return new AccessValidationResult(false, reason, AccessDenialType.PERMISSION_DENIED);
        }

        public static AccessValidationResult rateLimited(String reason) {
            return new AccessValidationResult(false, reason, AccessDenialType.RATE_LIMITED);
        }

        public static AccessValidationResult suspicious(String reason) {
            return new AccessValidationResult(false, reason, AccessDenialType.SUSPICIOUS_ACTIVITY);
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public AccessDenialType getDenialType() { return denialType; }
    }

    public enum AccessDenialType {
        PERMISSION_DENIED,
        RATE_LIMITED,
        SUSPICIOUS_ACTIVITY
    }

    /**
     * Statistics about share access patterns.
     */
    public static class ShareAccessStats {
        private final long totalAccesses;
        private final long viewCount;
        private final long downloadCount;
        private final long recentAccesses24h;
        private final List<Object[]> weeklyStatsByType;

        public ShareAccessStats(long totalAccesses, long viewCount, long downloadCount, 
                               long recentAccesses24h, List<Object[]> weeklyStatsByType) {
            this.totalAccesses = totalAccesses;
            this.viewCount = viewCount;
            this.downloadCount = downloadCount;
            this.recentAccesses24h = recentAccesses24h;
            this.weeklyStatsByType = weeklyStatsByType;
        }

        public long getTotalAccesses() { return totalAccesses; }
        public long getViewCount() { return viewCount; }
        public long getDownloadCount() { return downloadCount; }
        public long getRecentAccesses24h() { return recentAccesses24h; }
        public List<Object[]> getWeeklyStatsByType() { return weeklyStatsByType; }
    }

    /**
     * Report of suspicious activity patterns.
     */
    public static class SuspiciousActivityReport {
        private final String ipAddress;
        private final long accessCount;
        private final LocalDateTime periodStart;
        private final LocalDateTime periodEnd;
        private final String description;

        public SuspiciousActivityReport(String ipAddress, long accessCount, 
                                       LocalDateTime periodStart, LocalDateTime periodEnd, String description) {
            this.ipAddress = ipAddress;
            this.accessCount = accessCount;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
            this.description = description;
        }

        public String getIpAddress() { return ipAddress; }
        public long getAccessCount() { return accessCount; }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
        public String getDescription() { return description; }
    }

    /**
     * Current rate limit status for an IP address.
     */
    public static class RateLimitStatus {
        private final String ipAddress;
        private final long currentCount;
        private final long limit;
        private final LocalDateTime windowStart;
        private final boolean isLimited;

        public RateLimitStatus(String ipAddress, long currentCount, long limit, 
                              LocalDateTime windowStart, boolean isLimited) {
            this.ipAddress = ipAddress;
            this.currentCount = currentCount;
            this.limit = limit;
            this.windowStart = windowStart;
            this.isLimited = isLimited;
        }

        public String getIpAddress() { return ipAddress; }
        public long getCurrentCount() { return currentCount; }
        public long getLimit() { return limit; }
        public LocalDateTime getWindowStart() { return windowStart; }
        public boolean isLimited() { return isLimited; }
    }

    /**
     * Rate limiting information for caching.
     */
    private static class RateLimitInfo {
        private final LocalDateTime windowStart;
        private int count;

        public RateLimitInfo(LocalDateTime windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        public void increment() {
            this.count++;
        }

        public boolean isExpired(int windowHours) {
            return LocalDateTime.now().isAfter(windowStart.plusHours(windowHours));
        }

        public LocalDateTime getWindowStart() { return windowStart; }
        public int getCount() { return count; }
    }
}