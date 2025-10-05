package com.cloud.computing.filesharingapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for logging security-related events and audit trails.
 * Provides structured logging for verification attempts, security events, and system activities.
 */
@Service
public class SecurityAuditService {

    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Logs email verification attempt events.
     * 
     * @param email The email address involved in the verification
     * @param event The type of verification event
     * @param success Whether the event was successful
     * @param details Additional details about the event
     * @param ipAddress The IP address of the request (if available)
     */
    public void logVerificationEvent(String email, VerificationEvent event, boolean success, String details, String ipAddress) {
        try {
            // Set MDC context for structured logging
            MDC.put("event_type", "EMAIL_VERIFICATION");
            MDC.put("event_action", event.name());
            MDC.put("email", maskEmail(email));
            MDC.put("success", String.valueOf(success));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            
            if (ipAddress != null && !ipAddress.isEmpty()) {
                MDC.put("ip_address", ipAddress);
            }
            
            String logMessage = String.format("Verification Event: %s for email %s - %s. Details: %s", 
                event.getDescription(), 
                maskEmail(email), 
                success ? "SUCCESS" : "FAILED", 
                details != null ? details : "N/A");
            
            if (success) {
                securityLogger.info(logMessage);
            } else {
                securityLogger.warn(logMessage);
            }
            
        } finally {
            // Clear MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Logs rate limiting events.
     * 
     * @param email The email address that hit the rate limit
     * @param eventType The type of event that was rate limited
     * @param currentCount The current count of attempts
     * @param limit The rate limit threshold
     * @param ipAddress The IP address of the request (if available)
     */
    public void logRateLimitEvent(String email, String eventType, int currentCount, int limit, String ipAddress) {
        try {
            MDC.put("event_type", "RATE_LIMIT");
            MDC.put("event_action", eventType);
            MDC.put("email", maskEmail(email));
            MDC.put("current_count", String.valueOf(currentCount));
            MDC.put("limit", String.valueOf(limit));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            
            if (ipAddress != null && !ipAddress.isEmpty()) {
                MDC.put("ip_address", ipAddress);
            }
            
            String logMessage = String.format("Rate limit exceeded for %s: %s (%d/%d attempts)", 
                maskEmail(email), eventType, currentCount, limit);
            
            securityLogger.warn(logMessage);
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Logs cleanup and maintenance events.
     * 
     * @param operation The cleanup operation performed
     * @param recordsAffected Number of records affected
     * @param details Additional details about the operation
     */
    public void logMaintenanceEvent(String operation, int recordsAffected, String details) {
        try {
            MDC.put("event_type", "MAINTENANCE");
            MDC.put("event_action", operation);
            MDC.put("records_affected", String.valueOf(recordsAffected));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            
            String logMessage = String.format("Maintenance operation: %s completed. Records affected: %d. Details: %s", 
                operation, recordsAffected, details != null ? details : "N/A");
            
            securityLogger.info(logMessage);
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Logs suspicious activity or security violations.
     * 
     * @param email The email address involved (if applicable)
     * @param activity Description of the suspicious activity
     * @param severity The severity level of the security event
     * @param ipAddress The IP address of the request (if available)
     */
    public void logSecurityViolation(String email, String activity, SecuritySeverity severity, String ipAddress) {
        try {
            MDC.put("event_type", "SECURITY_VIOLATION");
            MDC.put("severity", severity.name());
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            
            if (email != null && !email.isEmpty()) {
                MDC.put("email", maskEmail(email));
            }
            
            if (ipAddress != null && !ipAddress.isEmpty()) {
                MDC.put("ip_address", ipAddress);
            }
            
            String logMessage = String.format("Security violation detected [%s]: %s", severity.name(), activity);
            
            switch (severity) {
                case LOW:
                    securityLogger.info(logMessage);
                    break;
                case MEDIUM:
                    securityLogger.warn(logMessage);
                    break;
                case HIGH:
                case CRITICAL:
                    securityLogger.error(logMessage);
                    break;
            }
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Logs system performance metrics related to verification operations.
     * 
     * @param operation The operation being measured
     * @param executionTimeMs Execution time in milliseconds
     * @param recordsProcessed Number of records processed
     */
    public void logPerformanceMetrics(String operation, long executionTimeMs, int recordsProcessed) {
        try {
            MDC.put("event_type", "PERFORMANCE");
            MDC.put("operation", operation);
            MDC.put("execution_time_ms", String.valueOf(executionTimeMs));
            MDC.put("records_processed", String.valueOf(recordsProcessed));
            MDC.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            
            String logMessage = String.format("Performance metrics for %s: %dms execution time, %d records processed", 
                operation, executionTimeMs, recordsProcessed);
            
            securityLogger.info(logMessage);
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Masks email address for privacy while keeping it identifiable for audit purposes.
     * Example: john.doe@example.com becomes j***e@e***e.com
     * 
     * @param email The email address to mask
     * @return Masked email address
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "N/A";
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex >= email.length() - 1) {
            return "***@***.***"; // Invalid email format
        }
        
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);
        
        // Mask local part
        String maskedLocal;
        if (localPart.length() <= 2) {
            maskedLocal = "*".repeat(localPart.length());
        } else {
            maskedLocal = localPart.charAt(0) + "*".repeat(localPart.length() - 2) + localPart.charAt(localPart.length() - 1);
        }
        
        // Mask domain part
        String maskedDomain;
        int dotIndex = domainPart.lastIndexOf('.');
        if (dotIndex > 0) {
            String domainName = domainPart.substring(0, dotIndex);
            String tld = domainPart.substring(dotIndex);
            
            if (domainName.length() <= 2) {
                maskedDomain = "*".repeat(domainName.length()) + tld;
            } else {
                maskedDomain = domainName.charAt(0) + "*".repeat(domainName.length() - 2) + 
                              domainName.charAt(domainName.length() - 1) + tld;
            }
        } else {
            maskedDomain = "*".repeat(domainPart.length());
        }
        
        return maskedLocal + "@" + maskedDomain;
    }

    /**
     * Enumeration of verification event types.
     */
    public enum VerificationEvent {
        CODE_GENERATED("Verification code generated"),
        CODE_SENT("Verification code sent via email"),
        CODE_VERIFIED("Verification code verified"),
        CODE_EXPIRED("Verification code expired"),
        CODE_INVALID("Invalid verification code provided"),
        CODE_RESENT("Verification code resent"),
        ACCOUNT_ACTIVATED("User account activated after verification"),
        CLEANUP_PERFORMED("Expired verification codes cleaned up");

        private final String description;

        VerificationEvent(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Enumeration of security severity levels.
     */
    public enum SecuritySeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}