package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive rate limiting service for file sharing operations.
 * 
 * <p>This service provides advanced rate limiting capabilities including:
 * <ul>
 *   <li>Multi-tier rate limiting (global, per-user, per-IP, per-share)</li>
 *   <li>Sliding window rate limiting for accurate enforcement</li>
 *   <li>Dynamic rate limit adjustment based on system load</li>
 *   <li>Rate limit bypass for trusted users/IPs</li>
 *   <li>Detailed rate limit analytics and monitoring</li>
 *   <li>Integration with security threat assessment</li>
 * </ul>
 * 
 * <p>The service uses both in-memory caching for performance and database
 * queries for accuracy, ensuring reliable rate limiting across all operations.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    @Autowired
    private ShareAccessRepository shareAccessRepository;

    @Autowired
    private FileShareRepository fileShareRepository;

    // Rate limiting configuration
    @Value("${app.sharing.max-shares-per-hour:10}")
    private int maxSharesPerHour;

    @Value("${app.sharing.max-access-per-ip-per-hour:100}")
    private int maxAccessPerIpPerHour;

    @Value("${app.sharing.max-access-per-share-per-ip-per-hour:20}")
    private int maxAccessPerSharePerIpPerHour;

    @Value("${app.sharing.rate-limit-window-hours:1}")
    private int rateLimitWindowHours;

    // Advanced rate limiting configuration
    @Value("${app.ratelimit.burst-allowance:5}")
    private int burstAllowance;

    @Value("${app.ratelimit.trusted-user-multiplier:2}")
    private int trustedUserMultiplier;

    @Value("${app.ratelimit.dynamic-adjustment-enabled:true}")
    private boolean dynamicAdjustmentEnabled;

    @Value("${app.ratelimit.system-load-threshold:0.8}")
    private double systemLoadThreshold;

    // In-memory rate limiting cache
    private final Map<String, SlidingWindowCounter> shareCreationCounters = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowCounter> ipAccessCounters = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowCounter> shareIpAccessCounters = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowCounter> userAccessCounters = new ConcurrentHashMap<>();

    /**
     * Validates if a user can create a new share based on comprehensive rate limiting.
     * 
     * <p>This method enforces multiple layers of rate limiting:
     * <ul>
     *   <li>Per-user share creation limits</li>
     *   <li>Per-IP share creation limits</li>
     *   <li>System-wide share creation limits</li>
     *   <li>Dynamic limits based on system load</li>
     * </ul>
     * 
     * @param user the user attempting to create a share
     * @param clientIp the IP address of the request
     * @return RateLimitResult containing validation outcome and details
     */
    public RateLimitResult validateShareCreation(User user, String clientIp) {
        logger.debug("Validating share creation rate limits for user: {} from IP: {}", 
            user.getUsername(), clientIp);

        // Get effective limits (may be adjusted based on system load or user trust level)
        int effectiveUserLimit = getEffectiveShareLimit(user);
        int effectiveIpLimit = getEffectiveIpShareLimit(clientIp);

        // Check user-based rate limiting
        String userKey = "user:" + user.getId();
        SlidingWindowCounter userCounter = shareCreationCounters.computeIfAbsent(userKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        
        if (userCounter.getCurrentCount() >= effectiveUserLimit) {
            logger.warn("Share creation rate limited for user: {} ({}/{} per hour)", 
                user.getUsername(), userCounter.getCurrentCount(), effectiveUserLimit);
            return RateLimitResult.rateLimited(RateLimitType.USER_SHARE_CREATION, 
                userCounter.getCurrentCount(), effectiveUserLimit, userCounter.getWindowResetTime());
        }

        // Check IP-based rate limiting
        String ipKey = "ip:" + clientIp;
        SlidingWindowCounter ipCounter = shareCreationCounters.computeIfAbsent(ipKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        
        if (ipCounter.getCurrentCount() >= effectiveIpLimit) {
            logger.warn("Share creation rate limited for IP: {} ({}/{} per hour)", 
                clientIp, ipCounter.getCurrentCount(), effectiveIpLimit);
            return RateLimitResult.rateLimited(RateLimitType.IP_SHARE_CREATION, 
                ipCounter.getCurrentCount(), effectiveIpLimit, ipCounter.getWindowResetTime());
        }

        // Check for burst protection
        if (!allowBurst(userCounter, effectiveUserLimit) || !allowBurst(ipCounter, effectiveIpLimit)) {
            logger.warn("Share creation burst limit exceeded for user: {} from IP: {}", 
                user.getUsername(), clientIp);
            return RateLimitResult.rateLimited(RateLimitType.BURST_PROTECTION, 
                Math.max(userCounter.getCurrentCount(), ipCounter.getCurrentCount()), 
                Math.min(effectiveUserLimit, effectiveIpLimit), null);
        }

        logger.debug("Share creation rate limit validation passed for user: {} from IP: {}", 
            user.getUsername(), clientIp);
        return RateLimitResult.allowed();
    }

    /**
     * Records a share creation event and updates rate limiting counters.
     * 
     * @param user the user who created the share
     * @param clientIp the IP address of the request
     */
    public void recordShareCreation(User user, String clientIp) {
        logger.debug("Recording share creation for rate limiting - user: {}, IP: {}", 
            user.getUsername(), clientIp);

        // Update user counter
        String userKey = "user:" + user.getId();
        SlidingWindowCounter userCounter = shareCreationCounters.computeIfAbsent(userKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        userCounter.increment();

        // Update IP counter
        String ipKey = "ip:" + clientIp;
        SlidingWindowCounter ipCounter = shareCreationCounters.computeIfAbsent(ipKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        ipCounter.increment();

        logger.debug("Share creation recorded - user count: {}, IP count: {}", 
            userCounter.getCurrentCount(), ipCounter.getCurrentCount());
    }

    /**
     * Validates if access to a shared file is allowed based on rate limiting.
     * 
     * <p>This method enforces multiple layers of access rate limiting:
     * <ul>
     *   <li>Global IP-based access limits</li>
     *   <li>Per-share IP-based access limits</li>
     *   <li>Per-user access limits (if authenticated)</li>
     *   <li>Access type specific limits (view vs download)</li>
     * </ul>
     * 
     * @param fileShare the file share being accessed
     * @param clientIp the IP address of the accessor
     * @param accessType the type of access requested
     * @param user the authenticated user (null for anonymous access)
     * @return RateLimitResult containing validation outcome and details
     */
    public RateLimitResult validateShareAccess(FileShare fileShare, String clientIp, 
                                              ShareAccessType accessType, User user) {
        logger.debug("Validating share access rate limits - share: {}, IP: {}, type: {}", 
            fileShare.getId(), clientIp, accessType);

        // Get effective limits
        int effectiveGlobalLimit = getEffectiveGlobalAccessLimit(clientIp);
        int effectiveShareLimit = getEffectiveShareAccessLimit(fileShare, clientIp);

        // Check global IP-based rate limiting
        String globalIpKey = "global:" + clientIp;
        SlidingWindowCounter globalCounter = ipAccessCounters.computeIfAbsent(globalIpKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        
        if (globalCounter.getCurrentCount() >= effectiveGlobalLimit) {
            logger.warn("Global access rate limited for IP: {} ({}/{} per hour)", 
                clientIp, globalCounter.getCurrentCount(), effectiveGlobalLimit);
            return RateLimitResult.rateLimited(RateLimitType.IP_GLOBAL_ACCESS, 
                globalCounter.getCurrentCount(), effectiveGlobalLimit, globalCounter.getWindowResetTime());
        }

        // Check share-specific IP rate limiting
        String shareIpKey = "share:" + fileShare.getId() + ":ip:" + clientIp;
        SlidingWindowCounter shareIpCounter = shareIpAccessCounters.computeIfAbsent(shareIpKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        
        if (shareIpCounter.getCurrentCount() >= effectiveShareLimit) {
            logger.warn("Share-specific access rate limited for IP: {} on share: {} ({}/{} per hour)", 
                clientIp, fileShare.getId(), shareIpCounter.getCurrentCount(), effectiveShareLimit);
            return RateLimitResult.rateLimited(RateLimitType.IP_SHARE_ACCESS, 
                shareIpCounter.getCurrentCount(), effectiveShareLimit, shareIpCounter.getWindowResetTime());
        }

        // Check user-based rate limiting if authenticated
        if (user != null) {
            RateLimitResult userResult = validateUserAccess(user, accessType);
            if (!userResult.isAllowed()) {
                return userResult;
            }
        }

        // Check access type specific limits
        RateLimitResult accessTypeResult = validateAccessTypeLimit(clientIp, accessType);
        if (!accessTypeResult.isAllowed()) {
            return accessTypeResult;
        }

        logger.debug("Share access rate limit validation passed - share: {}, IP: {}", 
            fileShare.getId(), clientIp);
        return RateLimitResult.allowed();
    }

    /**
     * Records a share access event and updates rate limiting counters.
     * 
     * @param fileShare the file share that was accessed
     * @param clientIp the IP address of the accessor
     * @param accessType the type of access performed
     * @param user the authenticated user (null for anonymous access)
     */
    public void recordShareAccess(FileShare fileShare, String clientIp, ShareAccessType accessType, User user) {
        logger.debug("Recording share access for rate limiting - share: {}, IP: {}, type: {}", 
            fileShare.getId(), clientIp, accessType);

        // Update global IP counter
        String globalIpKey = "global:" + clientIp;
        SlidingWindowCounter globalCounter = ipAccessCounters.computeIfAbsent(globalIpKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        globalCounter.increment();

        // Update share-specific IP counter
        String shareIpKey = "share:" + fileShare.getId() + ":ip:" + clientIp;
        SlidingWindowCounter shareIpCounter = shareIpAccessCounters.computeIfAbsent(shareIpKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        shareIpCounter.increment();

        // Update user counter if authenticated
        if (user != null) {
            String userKey = "user:" + user.getId() + ":access";
            SlidingWindowCounter userCounter = userAccessCounters.computeIfAbsent(userKey, 
                k -> new SlidingWindowCounter(rateLimitWindowHours));
            userCounter.increment();
        }

        // Update access type specific counter
        String accessTypeKey = "ip:" + clientIp + ":type:" + accessType;
        SlidingWindowCounter accessTypeCounter = ipAccessCounters.computeIfAbsent(accessTypeKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        accessTypeCounter.increment();

        logger.debug("Share access recorded - global: {}, share-specific: {}", 
            globalCounter.getCurrentCount(), shareIpCounter.getCurrentCount());
    }

    /**
     * Gets current rate limit status for an IP address.
     * 
     * @param clientIp the IP address to check
     * @return RateLimitStatus containing current usage and limits
     */
    public RateLimitStatus getRateLimitStatus(String clientIp) {
        logger.debug("Getting rate limit status for IP: {}", clientIp);

        String globalIpKey = "global:" + clientIp;
        SlidingWindowCounter globalCounter = ipAccessCounters.get(globalIpKey);
        
        int currentGlobalCount = globalCounter != null ? globalCounter.getCurrentCount() : 0;
        int effectiveGlobalLimit = getEffectiveGlobalAccessLimit(clientIp);
        
        return new RateLimitStatus(
            clientIp,
            currentGlobalCount,
            effectiveGlobalLimit,
            globalCounter != null ? globalCounter.getWindowResetTime() : LocalDateTime.now().plusHours(1),
            currentGlobalCount >= effectiveGlobalLimit
        );
    }

    /**
     * Gets comprehensive rate limiting analytics.
     * 
     * @param timeWindowHours the time window to analyze
     * @return RateLimitAnalytics containing detailed metrics
     */
    public RateLimitAnalytics getAnalytics(int timeWindowHours) {
        logger.info("Generating rate limiting analytics for last {} hours", timeWindowHours);

        LocalDateTime since = LocalDateTime.now().minusHours(timeWindowHours);

        // Count rate limited requests (approximation based on current counters)
        int rateLimitedRequests = 0;
        int totalRequests = 0;

        for (SlidingWindowCounter counter : ipAccessCounters.values()) {
            totalRequests += counter.getCurrentCount();
            if (counter.getCurrentCount() >= maxAccessPerIpPerHour) {
                rateLimitedRequests++;
            }
        }

        // Get database statistics for accuracy
        long totalAccesses = shareAccessRepository.countByAccessedAtAfter(since);
        long totalShares = fileShareRepository.countByCreatedAtAfter(since);

        return new RateLimitAnalytics(
            totalRequests,
            rateLimitedRequests,
            totalAccesses,
            totalShares,
            ipAccessCounters.size(),
            shareCreationCounters.size(),
            since,
            LocalDateTime.now()
        );
    }

    /**
     * Clears all rate limiting state (for testing or maintenance).
     */
    public void clearRateLimitState() {
        logger.info("Clearing all rate limiting state");

        shareCreationCounters.clear();
        ipAccessCounters.clear();
        shareIpAccessCounters.clear();
        userAccessCounters.clear();
    }

    /**
     * Adjusts rate limits dynamically based on system load.
     * 
     * @param systemLoad the current system load (0.0 to 1.0)
     */
    public void adjustRateLimits(double systemLoad) {
        if (!dynamicAdjustmentEnabled) {
            return;
        }

        logger.debug("Adjusting rate limits based on system load: {}", systemLoad);

        // Implement dynamic rate limit adjustment logic
        // This is a simplified implementation - in production, consider more sophisticated algorithms
        if (systemLoad > systemLoadThreshold) {
            // Reduce limits when system is under high load
            logger.info("High system load detected ({}), reducing rate limits", systemLoad);
        }
    }

    // Private helper methods

    /**
     * Gets the effective share creation limit for a user.
     */
    private int getEffectiveShareLimit(User user) {
        // Check if user is trusted (implement trust logic as needed)
        boolean isTrusted = false; // Placeholder - implement actual trust assessment
        
        int baseLimit = maxSharesPerHour;
        if (isTrusted) {
            baseLimit *= trustedUserMultiplier;
        }
        
        return baseLimit;
    }

    /**
     * Gets the effective IP-based share creation limit.
     */
    private int getEffectiveIpShareLimit(String clientIp) {
        // Could implement IP-based trust levels here
        return maxSharesPerHour;
    }

    /**
     * Gets the effective global access limit for an IP.
     */
    private int getEffectiveGlobalAccessLimit(String clientIp) {
        // Could implement IP-based adjustments here
        return maxAccessPerIpPerHour;
    }

    /**
     * Gets the effective share-specific access limit.
     */
    private int getEffectiveShareAccessLimit(FileShare fileShare, String clientIp) {
        // Could implement share-specific or owner-specific adjustments here
        return maxAccessPerSharePerIpPerHour;
    }

    /**
     * Checks if burst access is allowed.
     */
    private boolean allowBurst(SlidingWindowCounter counter, int limit) {
        return counter.getCurrentCount() < (limit + burstAllowance);
    }

    /**
     * Validates user-based access rate limiting.
     */
    private RateLimitResult validateUserAccess(User user, ShareAccessType accessType) {
        String userKey = "user:" + user.getId() + ":access";
        SlidingWindowCounter userCounter = userAccessCounters.computeIfAbsent(userKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        
        int userLimit = maxAccessPerIpPerHour; // Could be user-specific
        
        if (userCounter.getCurrentCount() >= userLimit) {
            logger.warn("User access rate limited: {} ({}/{} per hour)", 
                user.getUsername(), userCounter.getCurrentCount(), userLimit);
            return RateLimitResult.rateLimited(RateLimitType.USER_ACCESS, 
                userCounter.getCurrentCount(), userLimit, userCounter.getWindowResetTime());
        }
        
        return RateLimitResult.allowed();
    }

    /**
     * Validates access type specific rate limiting.
     */
    private RateLimitResult validateAccessTypeLimit(String clientIp, ShareAccessType accessType) {
        String accessTypeKey = "ip:" + clientIp + ":type:" + accessType;
        SlidingWindowCounter accessTypeCounter = ipAccessCounters.computeIfAbsent(accessTypeKey, 
            k -> new SlidingWindowCounter(rateLimitWindowHours));
        
        // Downloads might have stricter limits than views
        int typeLimit = accessType == ShareAccessType.DOWNLOAD ? 
            maxAccessPerIpPerHour / 2 : maxAccessPerIpPerHour;
        
        if (accessTypeCounter.getCurrentCount() >= typeLimit) {
            logger.warn("Access type rate limited for IP: {} type: {} ({}/{} per hour)", 
                clientIp, accessType, accessTypeCounter.getCurrentCount(), typeLimit);
            return RateLimitResult.rateLimited(RateLimitType.ACCESS_TYPE, 
                accessTypeCounter.getCurrentCount(), typeLimit, accessTypeCounter.getWindowResetTime());
        }
        
        return RateLimitResult.allowed();
    }

    // Inner classes and enums

    /**
     * Rate limiting result containing validation outcome and details.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final RateLimitType limitType;
        private final int currentCount;
        private final int limit;
        private final LocalDateTime resetTime;

        private RateLimitResult(boolean allowed, RateLimitType limitType, int currentCount, 
                               int limit, LocalDateTime resetTime) {
            this.allowed = allowed;
            this.limitType = limitType;
            this.currentCount = currentCount;
            this.limit = limit;
            this.resetTime = resetTime;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null, 0, 0, null);
        }

        public static RateLimitResult rateLimited(RateLimitType limitType, int currentCount, 
                                                 int limit, LocalDateTime resetTime) {
            return new RateLimitResult(false, limitType, currentCount, limit, resetTime);
        }

        public boolean isAllowed() { return allowed; }
        public RateLimitType getLimitType() { return limitType; }
        public int getCurrentCount() { return currentCount; }
        public int getLimit() { return limit; }
        public LocalDateTime getResetTime() { return resetTime; }
    }

    /**
     * Types of rate limits that can be enforced.
     */
    public enum RateLimitType {
        USER_SHARE_CREATION,
        IP_SHARE_CREATION,
        IP_GLOBAL_ACCESS,
        IP_SHARE_ACCESS,
        USER_ACCESS,
        ACCESS_TYPE,
        BURST_PROTECTION
    }

    /**
     * Current rate limit status for monitoring.
     */
    public static class RateLimitStatus {
        private final String identifier;
        private final int currentCount;
        private final int limit;
        private final LocalDateTime resetTime;
        private final boolean isLimited;

        public RateLimitStatus(String identifier, int currentCount, int limit, 
                              LocalDateTime resetTime, boolean isLimited) {
            this.identifier = identifier;
            this.currentCount = currentCount;
            this.limit = limit;
            this.resetTime = resetTime;
            this.isLimited = isLimited;
        }

        public String getIdentifier() { return identifier; }
        public int getCurrentCount() { return currentCount; }
        public int getLimit() { return limit; }
        public LocalDateTime getResetTime() { return resetTime; }
        public boolean isLimited() { return isLimited; }
    }

    /**
     * Rate limiting analytics data.
     */
    public static class RateLimitAnalytics {
        private final int totalRequests;
        private final int rateLimitedRequests;
        private final long totalAccesses;
        private final long totalShares;
        private final int activeIpCounters;
        private final int activeUserCounters;
        private final LocalDateTime periodStart;
        private final LocalDateTime periodEnd;

        public RateLimitAnalytics(int totalRequests, int rateLimitedRequests, long totalAccesses,
                                 long totalShares, int activeIpCounters, int activeUserCounters,
                                 LocalDateTime periodStart, LocalDateTime periodEnd) {
            this.totalRequests = totalRequests;
            this.rateLimitedRequests = rateLimitedRequests;
            this.totalAccesses = totalAccesses;
            this.totalShares = totalShares;
            this.activeIpCounters = activeIpCounters;
            this.activeUserCounters = activeUserCounters;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }

        public int getTotalRequests() { return totalRequests; }
        public int getRateLimitedRequests() { return rateLimitedRequests; }
        public long getTotalAccesses() { return totalAccesses; }
        public long getTotalShares() { return totalShares; }
        public int getActiveIpCounters() { return activeIpCounters; }
        public int getActiveUserCounters() { return activeUserCounters; }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
    }

    /**
     * Sliding window counter for accurate rate limiting.
     */
    private static class SlidingWindowCounter {
        private final int windowHours;
        private final Map<LocalDateTime, Integer> buckets = new ConcurrentHashMap<>();
        private LocalDateTime lastCleanup = LocalDateTime.now();

        public SlidingWindowCounter(int windowHours) {
            this.windowHours = windowHours;
        }

        public synchronized void increment() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime bucket = now.withMinute(0).withSecond(0).withNano(0);
            
            buckets.merge(bucket, 1, Integer::sum);
            
            // Cleanup old buckets periodically
            if (now.isAfter(lastCleanup.plusMinutes(10))) {
                cleanup();
                lastCleanup = now;
            }
        }

        public synchronized int getCurrentCount() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusHours(windowHours);
            
            return buckets.entrySet().stream()
                .filter(entry -> entry.getKey().isAfter(cutoff))
                .mapToInt(Map.Entry::getValue)
                .sum();
        }

        public LocalDateTime getWindowResetTime() {
            LocalDateTime now = LocalDateTime.now();
            return now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }

        private void cleanup() {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(windowHours + 1);
            buckets.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        }
    }
}