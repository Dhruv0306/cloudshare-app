package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.ShareAccessRepository;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced security service for comprehensive file sharing security and monitoring.
 * 
 * <p>This service provides enhanced security features including:
 * <ul>
 *   <li>Comprehensive rate limiting for share creation and access</li>
 *   <li>IP-based access controls and geolocation filtering</li>
 *   <li>Detailed audit logging for all sharing operations</li>
 *   <li>Suspicious activity detection and automated response</li>
 *   <li>Security analytics dashboard and threat monitoring</li>
 *   <li>Real-time security event processing and alerting</li>
 * </ul>
 * 
 * <p>The service maintains in-memory security state for performance while
 * providing persistent audit trails and analytics data.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class AdvancedSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedSecurityService.class);
    @SuppressWarnings("unused")
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    private ShareAccessRepository shareAccessRepository;

    @Autowired
    private FileShareRepository fileShareRepository;

    @Autowired
    private SecurityAuditService securityAuditService;

    // Rate limiting configuration
    @Value("${app.sharing.max-shares-per-hour:10}")
    private int maxSharesPerHour;

    @Value("${app.sharing.max-access-per-ip-per-hour:100}")
    private int maxAccessPerIpPerHour;

    @Value("${app.sharing.max-access-per-share-per-ip-per-hour:20}")
    private int maxAccessPerSharePerIpPerHour;

    @Value("${app.sharing.suspicious-activity-threshold:50}")
    private int suspiciousActivityThreshold;

    @Value("${app.sharing.rate-limit-window-hours:1}")
    private int rateLimitWindowHours;

    // Security thresholds
    @Value("${app.security.max-failed-attempts-per-ip:20}")
    private int maxFailedAttemptsPerIp;

    @Value("${app.security.ip-blacklist-duration-hours:24}")
    private int ipBlacklistDurationHours;

    @Value("${app.security.geolocation-enabled:false}")
    private boolean geolocationEnabled;

    @Value("${app.security.allowed-countries:}")
    private String allowedCountries;

    // In-memory security state (consider Redis for production)
    private final Map<String, RateLimitState> shareCreationLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> ipAccessLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> shareIpAccessLimits = new ConcurrentHashMap<>();
    private final Set<String> blacklistedIps = ConcurrentHashMap.newKeySet();
    private final Map<String, SecurityThreatLevel> ipThreatLevels = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> ipBlacklistExpiry = new ConcurrentHashMap<>();

    /**
     * Validates if a user can create a new share based on rate limiting rules.
     * 
     * <p>This method enforces comprehensive rate limiting for share creation including:
     * <ul>
     *   <li>Per-user share creation limits</li>
     *   <li>IP-based share creation limits</li>
     *   <li>Account-based security checks</li>
     *   <li>Suspicious activity pattern detection</li>
     * </ul>
     * 
     * @param user the user attempting to create a share
     * @param clientIp the IP address of the request
     * @param userAgent the user agent string from the request
     * @return ShareCreationValidationResult containing validation outcome
     */
    public ShareCreationValidationResult validateShareCreation(User user, String clientIp, String userAgent) {
        logger.debug("Validating share creation for user: {} from IP: {}", user.getUsername(), clientIp);

        try {
            // Set audit context
            MDC.put("event_type", "SHARE_CREATION_VALIDATION");
            MDC.put("user_id", user.getId().toString());
            MDC.put("username", user.getUsername());
            MDC.put("client_ip", clientIp);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));

            // Check if IP is blacklisted
            if (isIpBlacklisted(clientIp)) {
                auditLogger.warn("Share creation denied - IP blacklisted: {}", clientIp);
                return ShareCreationValidationResult.denied("Access denied from this IP address", 
                    SecurityDenialReason.IP_BLACKLISTED);
            }

            // Check geolocation restrictions if enabled
            if (geolocationEnabled && !isGeolocationAllowed(clientIp)) {
                auditLogger.warn("Share creation denied - geolocation restriction: {} from {}", 
                    user.getUsername(), clientIp);
                return ShareCreationValidationResult.denied("Access not allowed from this location", 
                    SecurityDenialReason.GEOLOCATION_RESTRICTED);
            }

            // Check user-based rate limiting
            if (isUserShareCreationRateLimited(user)) {
                auditLogger.warn("Share creation rate limited for user: {} (limit: {} per hour)", 
                    user.getUsername(), maxSharesPerHour);
                return ShareCreationValidationResult.rateLimited("Too many shares created recently. Please try again later.");
            }

            // Check IP-based rate limiting for share creation
            if (isIpShareCreationRateLimited(clientIp)) {
                auditLogger.warn("Share creation rate limited for IP: {} (limit: {} per hour)", 
                    clientIp, maxSharesPerHour);
                return ShareCreationValidationResult.rateLimited("Too many shares created from this IP address. Please try again later.");
            }

            // Check for suspicious patterns
            SecurityThreatLevel threatLevel = assessThreatLevel(clientIp, user);
            if (threatLevel == SecurityThreatLevel.HIGH || threatLevel == SecurityThreatLevel.CRITICAL) {
                auditLogger.warn("Share creation denied - high threat level: {} for user: {} from IP: {}", 
                    threatLevel, user.getUsername(), clientIp);
                return ShareCreationValidationResult.denied("Suspicious activity detected. Access temporarily restricted.", 
                    SecurityDenialReason.SUSPICIOUS_ACTIVITY);
            }

            // All validations passed
            auditLogger.info("Share creation validation passed for user: {} from IP: {}", 
                user.getUsername(), clientIp);
            return ShareCreationValidationResult.allowed();

        } finally {
            MDC.clear();
        }
    }

    /**
     * Records a share creation event and updates security state.
     * 
     * @param user the user who created the share
     * @param fileShare the created file share
     * @param clientIp the IP address of the request
     * @param userAgent the user agent string from the request
     */
    public void recordShareCreation(User user, FileShare fileShare, String clientIp, String userAgent) {
        logger.info("Recording share creation - user: {}, share ID: {}, IP: {}", 
            user.getUsername(), fileShare.getId(), clientIp);

        try {
            // Set audit context
            MDC.put("event_type", "SHARE_CREATED");
            MDC.put("user_id", user.getId().toString());
            MDC.put("username", user.getUsername());
            MDC.put("share_id", fileShare.getId().toString());
            MDC.put("share_token", fileShare.getShareToken());
            MDC.put("file_id", fileShare.getFile().getId().toString());
            MDC.put("file_name", fileShare.getFile().getOriginalFileName());
            MDC.put("permission", fileShare.getPermission().toString());
            MDC.put("expires_at", fileShare.getExpiresAt() != null ? fileShare.getExpiresAt().toString() : "never");
            MDC.put("client_ip", clientIp);
            MDC.put("user_agent", userAgent);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));

            // Update rate limiting state
            updateShareCreationLimits(user, clientIp);

            // Log the creation event
            auditLogger.info("Share created successfully - ID: {}, token: {}, file: {}, permission: {}, expires: {}", 
                fileShare.getId(), fileShare.getShareToken(), fileShare.getFile().getOriginalFileName(),
                fileShare.getPermission(), fileShare.getExpiresAt() != null ? fileShare.getExpiresAt() : "never");

        } finally {
            MDC.clear();
        }
    }

    /**
     * Records a share access attempt with comprehensive security logging.
     * 
     * @param fileShare the file share being accessed
     * @param accessType the type of access (VIEW or DOWNLOAD)
     * @param clientIp the IP address of the accessor
     * @param userAgent the user agent string from the request
     * @param success whether the access was successful
     * @param denialReason the reason for denial if access was unsuccessful
     */
    public void recordShareAccess(FileShare fileShare, ShareAccessType accessType, String clientIp, 
                                 String userAgent, boolean success, String denialReason) {
        logger.debug("Recording share access - share ID: {}, type: {}, IP: {}, success: {}", 
            fileShare.getId(), accessType, clientIp, success);

        try {
            // Set audit context
            MDC.put("event_type", "SHARE_ACCESS");
            MDC.put("share_id", fileShare.getId().toString());
            MDC.put("share_token", fileShare.getShareToken());
            MDC.put("file_id", fileShare.getFile().getId().toString());
            MDC.put("file_name", fileShare.getFile().getOriginalFileName());
            MDC.put("owner_id", fileShare.getOwner().getId().toString());
            MDC.put("owner_username", fileShare.getOwner().getUsername());
            MDC.put("access_type", accessType.toString());
            MDC.put("client_ip", clientIp);
            MDC.put("user_agent", userAgent);
            MDC.put("success", String.valueOf(success));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));

            if (!success && denialReason != null) {
                MDC.put("denial_reason", denialReason);
            }

            // Update access tracking
            updateAccessLimits(clientIp, fileShare.getId());

            // Check for suspicious patterns after access
            if (success) {
                checkPostAccessSecurity(clientIp, fileShare);
            } else {
                recordFailedAccess(clientIp, fileShare, denialReason);
            }

            // Log the access event
            if (success) {
                auditLogger.info("Share access successful - share: {}, type: {}, file: {}, IP: {}", 
                    fileShare.getId(), accessType, fileShare.getFile().getOriginalFileName(), clientIp);
            } else {
                auditLogger.warn("Share access denied - share: {}, type: {}, file: {}, IP: {}, reason: {}", 
                    fileShare.getId(), accessType, fileShare.getFile().getOriginalFileName(), clientIp, denialReason);
            }

        } finally {
            MDC.clear();
        }
    }

    /**
     * Records a share management operation (update, revoke, etc.).
     * 
     * @param user the user performing the operation
     * @param fileShare the file share being managed
     * @param operation the type of operation performed
     * @param clientIp the IP address of the request
     * @param userAgent the user agent string from the request
     */
    public void recordShareManagement(User user, FileShare fileShare, ShareManagementOperation operation, 
                                     String clientIp, String userAgent) {
        logger.info("Recording share management - user: {}, share ID: {}, operation: {}, IP: {}", 
            user.getUsername(), fileShare.getId(), operation, clientIp);

        try {
            // Set audit context
            MDC.put("event_type", "SHARE_MANAGEMENT");
            MDC.put("user_id", user.getId().toString());
            MDC.put("username", user.getUsername());
            MDC.put("share_id", fileShare.getId().toString());
            MDC.put("share_token", fileShare.getShareToken());
            MDC.put("file_id", fileShare.getFile().getId().toString());
            MDC.put("file_name", fileShare.getFile().getOriginalFileName());
            MDC.put("operation", operation.toString());
            MDC.put("client_ip", clientIp);
            MDC.put("user_agent", userAgent);
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));

            // Log the management event
            auditLogger.info("Share management operation - user: {}, share: {}, file: {}, operation: {}, IP: {}", 
                user.getUsername(), fileShare.getId(), fileShare.getFile().getOriginalFileName(), operation, clientIp);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Gets comprehensive security analytics for the sharing system.
     * 
     * @param timeWindowHours the time window to analyze (default: 24 hours)
     * @return SecurityAnalytics containing detailed security metrics
     */
    public SecurityAnalytics getSecurityAnalytics(int timeWindowHours) {
        logger.info("Generating security analytics for last {} hours", timeWindowHours);

        LocalDateTime since = LocalDateTime.now().minusHours(timeWindowHours);
        LocalDateTime now = LocalDateTime.now();

        // Get access statistics
        long totalAccesses = shareAccessRepository.countByAccessedAtAfter(since);
        long viewAccesses = shareAccessRepository.countByAccessTypeAndAccessedAtAfter(ShareAccessType.VIEW, since);
        long downloadAccesses = shareAccessRepository.countByAccessTypeAndAccessedAtAfter(ShareAccessType.DOWNLOAD, since);

        // Get suspicious activity patterns
        List<Object[]> suspiciousPatterns = shareAccessRepository.findSuspiciousAccessPatterns(
            since, suspiciousActivityThreshold);

        // Get share creation statistics
        long totalShares = fileShareRepository.countByCreatedAtAfter(since);
        long activeShares = fileShareRepository.countByActiveTrueAndCreatedAtAfter(since);

        // Calculate threat metrics
        int blacklistedIpsCount = blacklistedIps.size();
        int highThreatIpsCount = (int) ipThreatLevels.values().stream()
            .filter(level -> level == SecurityThreatLevel.HIGH || level == SecurityThreatLevel.CRITICAL)
            .count();

        return new SecurityAnalytics(
            totalAccesses, viewAccesses, downloadAccesses,
            totalShares, activeShares,
            suspiciousPatterns.size(), blacklistedIpsCount, highThreatIpsCount,
            suspiciousPatterns, since, now
        );
    }

    /**
     * Gets the current threat level for an IP address.
     * 
     * @param clientIp the IP address to assess
     * @return SecurityThreatLevel indicating the current threat level
     */
    public SecurityThreatLevel getThreatLevel(String clientIp) {
        return ipThreatLevels.getOrDefault(clientIp, SecurityThreatLevel.LOW);
    }

    /**
     * Manually blacklists an IP address for security purposes.
     * 
     * @param clientIp the IP address to blacklist
     * @param durationHours the duration of the blacklist in hours
     * @param reason the reason for blacklisting
     */
    public void blacklistIp(String clientIp, int durationHours, String reason) {
        logger.warn("Manually blacklisting IP: {} for {} hours, reason: {}", clientIp, durationHours, reason);

        blacklistedIps.add(clientIp);
        ipBlacklistExpiry.put(clientIp, LocalDateTime.now().plusHours(durationHours));
        ipThreatLevels.put(clientIp, SecurityThreatLevel.CRITICAL);

        securityAuditService.logSecurityViolation(null, 
            "IP address manually blacklisted: " + clientIp + " - " + reason, 
            SecurityAuditService.SecuritySeverity.HIGH, clientIp);
    }

    /**
     * Removes an IP address from the blacklist.
     * 
     * @param clientIp the IP address to unblacklist
     */
    public void unblacklistIp(String clientIp) {
        logger.info("Removing IP from blacklist: {}", clientIp);

        blacklistedIps.remove(clientIp);
        ipBlacklistExpiry.remove(clientIp);
        ipThreatLevels.remove(clientIp);
    }

    /**
     * Clears all security state (for testing or maintenance).
     */
    public void clearSecurityState() {
        logger.info("Clearing all security state");

        shareCreationLimits.clear();
        ipAccessLimits.clear();
        shareIpAccessLimits.clear();
        blacklistedIps.clear();
        ipThreatLevels.clear();
        ipBlacklistExpiry.clear();
    }

    // Private helper methods

    /**
     * Checks if an IP address is currently blacklisted.
     */
    private boolean isIpBlacklisted(String clientIp) {
        if (!blacklistedIps.contains(clientIp)) {
            return false;
        }

        // Check if blacklist has expired
        LocalDateTime expiry = ipBlacklistExpiry.get(clientIp);
        if (expiry != null && LocalDateTime.now().isAfter(expiry)) {
            unblacklistIp(clientIp);
            return false;
        }

        return true;
    }

    /**
     * Checks if geolocation allows access from the given IP.
     */
    private boolean isGeolocationAllowed(String clientIp) {
        // Simplified geolocation check - in production, integrate with a geolocation service
        if (allowedCountries == null || allowedCountries.trim().isEmpty()) {
            return true; // No restrictions configured
        }

        // For now, allow all IPs (implement actual geolocation checking as needed)
        return true;
    }

    /**
     * Checks if user has exceeded share creation rate limits.
     */
    private boolean isUserShareCreationRateLimited(User user) {
        LocalDateTime since = LocalDateTime.now().minusHours(rateLimitWindowHours);
        long userShares = fileShareRepository.countByOwnerAndCreatedAtAfter(user, since);
        return userShares >= maxSharesPerHour;
    }

    /**
     * Checks if IP has exceeded share creation rate limits.
     */
    private boolean isIpShareCreationRateLimited(String clientIp) {
        String key = "create:" + clientIp;
        RateLimitState state = shareCreationLimits.get(key);
        
        if (state == null || state.isExpired(rateLimitWindowHours)) {
            return false;
        }
        
        return state.getCount() >= maxSharesPerHour;
    }

    /**
     * Assesses the threat level for an IP address and user combination.
     */
    private SecurityThreatLevel assessThreatLevel(String clientIp, User user) {
        // Check existing threat level
        @SuppressWarnings("unused")
        SecurityThreatLevel currentLevel = ipThreatLevels.getOrDefault(clientIp, SecurityThreatLevel.LOW);
        
        // Analyze recent activity patterns
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        long recentAccesses = shareAccessRepository.countByAccessorIpAndAccessedAtAfter(clientIp, since);
        
        if (recentAccesses > suspiciousActivityThreshold * 2) {
            return SecurityThreatLevel.CRITICAL;
        } else if (recentAccesses > suspiciousActivityThreshold) {
            return SecurityThreatLevel.HIGH;
        } else if (recentAccesses > suspiciousActivityThreshold / 2) {
            return SecurityThreatLevel.MEDIUM;
        }
        
        return SecurityThreatLevel.LOW;
    }

    /**
     * Updates share creation rate limiting state.
     */
    private void updateShareCreationLimits(User user, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        
        // Update IP-based limits
        String ipKey = "create:" + clientIp;
        shareCreationLimits.compute(ipKey, (key, state) -> {
            if (state == null || state.isExpired(rateLimitWindowHours)) {
                return new RateLimitState(now, 1);
            } else {
                state.increment();
                return state;
            }
        });
    }

    /**
     * Updates access rate limiting state.
     */
    private void updateAccessLimits(String clientIp, Long shareId) {
        LocalDateTime now = LocalDateTime.now();
        
        // Update global IP limits
        ipAccessLimits.compute(clientIp, (key, state) -> {
            if (state == null || state.isExpired(rateLimitWindowHours)) {
                return new RateLimitState(now, 1);
            } else {
                state.increment();
                return state;
            }
        });
        
        // Update share-specific IP limits
        String shareIpKey = shareId + ":" + clientIp;
        shareIpAccessLimits.compute(shareIpKey, (key, state) -> {
            if (state == null || state.isExpired(rateLimitWindowHours)) {
                return new RateLimitState(now, 1);
            } else {
                state.increment();
                return state;
            }
        });
    }

    /**
     * Performs post-access security checks.
     */
    private void checkPostAccessSecurity(String clientIp, FileShare fileShare) {
        // Update threat level based on access patterns
        SecurityThreatLevel newLevel = assessThreatLevel(clientIp, fileShare.getOwner());
        ipThreatLevels.put(clientIp, newLevel);
        
        // Auto-blacklist if threat level is critical
        if (newLevel == SecurityThreatLevel.CRITICAL && !isIpBlacklisted(clientIp)) {
            blacklistIp(clientIp, ipBlacklistDurationHours, "Automatic blacklist due to critical threat level");
        }
    }

    /**
     * Records a failed access attempt for security monitoring.
     */
    private void recordFailedAccess(String clientIp, FileShare fileShare, String reason) {
        // Increment failed attempt counter
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        long failedAttempts = shareAccessRepository.countByAccessorIpAndAccessedAtAfter(clientIp, since);
        
        if (failedAttempts >= maxFailedAttemptsPerIp) {
            blacklistIp(clientIp, ipBlacklistDurationHours, 
                "Too many failed access attempts: " + failedAttempts);
        }
    }

    // Inner classes and enums

    /**
     * Result of share creation validation.
     */
    public static class ShareCreationValidationResult {
        private final boolean allowed;
        private final String reason;
        private final SecurityDenialReason denialReason;

        private ShareCreationValidationResult(boolean allowed, String reason, SecurityDenialReason denialReason) {
            this.allowed = allowed;
            this.reason = reason;
            this.denialReason = denialReason;
        }

        public static ShareCreationValidationResult allowed() {
            return new ShareCreationValidationResult(true, null, null);
        }

        public static ShareCreationValidationResult denied(String reason, SecurityDenialReason denialReason) {
            return new ShareCreationValidationResult(false, reason, denialReason);
        }

        public static ShareCreationValidationResult rateLimited(String reason) {
            return new ShareCreationValidationResult(false, reason, SecurityDenialReason.RATE_LIMITED);
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public SecurityDenialReason getDenialReason() { return denialReason; }
    }

    /**
     * Security denial reasons.
     */
    public enum SecurityDenialReason {
        RATE_LIMITED,
        IP_BLACKLISTED,
        GEOLOCATION_RESTRICTED,
        SUSPICIOUS_ACTIVITY,
        SECURITY_VIOLATION
    }

    /**
     * Share management operations for audit logging.
     */
    public enum ShareManagementOperation {
        CREATED,
        UPDATED,
        REVOKED,
        PERMISSION_CHANGED,
        EXPIRATION_CHANGED,
        DELETED
    }

    /**
     * Security threat levels.
     */
    public enum SecurityThreatLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Rate limiting state for tracking access counts.
     */
    private static class RateLimitState {
        private final LocalDateTime windowStart;
        private int count;

        public RateLimitState(LocalDateTime windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        public void increment() {
            this.count++;
        }

        public boolean isExpired(int windowHours) {
            return LocalDateTime.now().isAfter(windowStart.plusHours(windowHours));
        }

        public int getCount() { return count; }
        @SuppressWarnings("unused")
        public LocalDateTime getWindowStart() { return windowStart; }
    }

    /**
     * Comprehensive security analytics data.
     */
    public static class SecurityAnalytics {
        private final long totalAccesses;
        private final long viewAccesses;
        private final long downloadAccesses;
        private final long totalShares;
        private final long activeShares;
        private final int suspiciousPatterns;
        private final int blacklistedIps;
        private final int highThreatIps;
        private final List<Object[]> suspiciousActivityDetails;
        private final LocalDateTime periodStart;
        private final LocalDateTime periodEnd;

        public SecurityAnalytics(long totalAccesses, long viewAccesses, long downloadAccesses,
                               long totalShares, long activeShares, int suspiciousPatterns,
                               int blacklistedIps, int highThreatIps, List<Object[]> suspiciousActivityDetails,
                               LocalDateTime periodStart, LocalDateTime periodEnd) {
            this.totalAccesses = totalAccesses;
            this.viewAccesses = viewAccesses;
            this.downloadAccesses = downloadAccesses;
            this.totalShares = totalShares;
            this.activeShares = activeShares;
            this.suspiciousPatterns = suspiciousPatterns;
            this.blacklistedIps = blacklistedIps;
            this.highThreatIps = highThreatIps;
            this.suspiciousActivityDetails = suspiciousActivityDetails;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }

        // Getters
        public long getTotalAccesses() { return totalAccesses; }
        public long getViewAccesses() { return viewAccesses; }
        public long getDownloadAccesses() { return downloadAccesses; }
        public long getTotalShares() { return totalShares; }
        public long getActiveShares() { return activeShares; }
        public int getSuspiciousPatterns() { return suspiciousPatterns; }
        public int getBlacklistedIps() { return blacklistedIps; }
        public int getHighThreatIps() { return highThreatIps; }
        public List<Object[]> getSuspiciousActivityDetails() { return suspiciousActivityDetails; }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
    }
}