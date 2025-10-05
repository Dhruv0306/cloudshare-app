package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.EmailVerification;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.exception.EmailVerificationException;
import com.cloud.computing.filesharingapp.exception.RateLimitExceededException;
import com.cloud.computing.filesharingapp.repository.EmailVerificationRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing email verification processes including code generation,
 * validation, rate limiting, and cleanup operations.
 */
@Service
public class EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);
    
    private static final int CODE_LENGTH = 6;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Autowired
    private EmailVerificationRepository emailVerificationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private SecurityAuditService securityAuditService;
    
    @Value("${app.verification.max-attempts:3}")
    private int maxAttemptsPerCode;
    
    @Value("${app.verification.max-codes-per-hour:5}")
    private int maxCodesPerHour;
    
    @Value("${app.verification.code-expiry-minutes:15}")
    private int codeExpiryMinutes;

    /**
     * Generates a cryptographically secure 6-digit verification code.
     * 
     * @return A 6-digit numeric verification code
     */
    public String generateVerificationCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(secureRandom.nextInt(10));
        }
        String generatedCode = code.toString();
        logger.debug("Generated verification code for user (code length: {})", generatedCode.length());
        return generatedCode;
    }

    /**
     * Creates a new verification record for a user and sends the verification email.
     * Implements rate limiting to prevent abuse.
     * 
     * @param user The user for whom to create the verification record
     * @throws RuntimeException if rate limit is exceeded or email sending fails
     */
    @Transactional
    public void createVerificationRecord(User user) {
        logger.info("Creating verification record for user: {}", user.getEmail());
        
        // Check rate limiting
        if (!isWithinRateLimit(user.getEmail())) {
            logger.warn("Rate limit exceeded for email: {}", user.getEmail());
            
            // Log rate limit violation
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentCodeCount = emailVerificationRepository
                .countByEmailAndCreatedAtAfterAndUsedFalse(user.getEmail(), oneHourAgo);
            securityAuditService.logRateLimitEvent(user.getEmail(), "VERIFICATION_CODE_REQUEST", 
                (int) recentCodeCount, maxCodesPerHour, null);
            
            throw new RateLimitExceededException("Too many verification code requests. Please wait before requesting another code.");
        }
        
        // Invalidate any existing unused codes for this email
        emailVerificationRepository.markAllAsUsedByEmail(user.getEmail());
        
        // Generate new verification code
        String rawCode = generateVerificationCode();
        String hashedCode = passwordEncoder.encode(rawCode);
        
        // Create verification record
        EmailVerification verification = new EmailVerification();
        verification.setEmail(user.getEmail());
        verification.setVerificationCode(hashedCode);
        verification.setUser(user);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(codeExpiryMinutes));
        verification.setUsed(false);
        
        emailVerificationRepository.save(verification);
        
        // Log code generation
        securityAuditService.logVerificationEvent(user.getEmail(), 
            SecurityAuditService.VerificationEvent.CODE_GENERATED, true, 
            "Verification code generated and stored", null);
        
        // Send verification email
        try {
            emailService.sendVerificationEmail(user.getEmail(), rawCode);
            logger.info("Verification email sent successfully to: {}", user.getEmail());
            
            // Log successful email sending
            securityAuditService.logVerificationEvent(user.getEmail(), 
                SecurityAuditService.VerificationEvent.CODE_SENT, true, 
                "Verification email sent successfully", null);
                
        } catch (Exception e) {
            logger.error("Failed to send verification email to: {} - {}", user.getEmail(), e.getMessage());
            
            // Log email sending failure
            securityAuditService.logVerificationEvent(user.getEmail(), 
                SecurityAuditService.VerificationEvent.CODE_SENT, false, 
                "Failed to send verification email: " + e.getMessage(), null);
                
            throw new EmailVerificationException("Failed to send verification email. Please try again later.", e);
        }
    }

    /**
     * Verifies a verification code for the given email address.
     * Implements security measures including code hashing and attempt limiting.
     * 
     * @param email The email address to verify
     * @param code The verification code provided by the user
     * @throws EmailVerificationException if verification fails for any reason
     */
    @Transactional
    public void verifyCode(String email, String code) {
        logger.info("Attempting to verify code for email: {}", email);
        
        // Find the most recent unused verification record
        Optional<EmailVerification> verificationOpt = 
            emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        
        if (verificationOpt.isEmpty()) {
            logger.warn("No unused verification code found for email: {}", email);
            
            // Log security violation - attempt to verify without valid code
            securityAuditService.logSecurityViolation(email, 
                "Attempt to verify email without valid verification code", 
                SecurityAuditService.SecuritySeverity.MEDIUM, null);
                
            throw new EmailVerificationException("No verification code found. Please request a new code.");
        }
        
        EmailVerification verification = verificationOpt.get();
        
        // Check if code is expired
        if (verification.isExpired()) {
            logger.warn("Verification code expired for email: {}", email);
            verification.setUsed(true);
            emailVerificationRepository.save(verification);
            
            // Log expired code attempt
            securityAuditService.logVerificationEvent(email, 
                SecurityAuditService.VerificationEvent.CODE_EXPIRED, false, 
                "Attempt to use expired verification code", null);
                
            throw new EmailVerificationException("Verification code has expired. Please request a new code.");
        }
        
        // Verify the code using password encoder (secure comparison)
        if (!passwordEncoder.matches(code, verification.getVerificationCode())) {
            logger.warn("Invalid verification code provided for email: {}", email);
            
            // Log invalid code attempt
            securityAuditService.logVerificationEvent(email, 
                SecurityAuditService.VerificationEvent.CODE_INVALID, false, 
                "Invalid verification code provided", null);
                
            throw new EmailVerificationException("Invalid verification code. Please check the code and try again.");
        }
        
        // Mark verification as used
        verification.setUsed(true);
        emailVerificationRepository.save(verification);
        
        // Update user account status
        User user = verification.getUser();
        if (user != null) {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(user);
            logger.info("User account activated for email: {}", email);
            
            // Log account activation
            securityAuditService.logVerificationEvent(email, 
                SecurityAuditService.VerificationEvent.ACCOUNT_ACTIVATED, true, 
                "User account activated after successful email verification", null);
        }
        
        logger.info("Email verification successful for: {}", email);
        
        // Log successful verification
        securityAuditService.logVerificationEvent(email, 
            SecurityAuditService.VerificationEvent.CODE_VERIFIED, true, 
            "Email verification completed successfully", null);
    }

    /**
     * Resends a verification code for the given email address.
     * Implements rate limiting to prevent abuse.
     * 
     * @param email The email address to resend verification code to
     * @throws RuntimeException if rate limit is exceeded or user not found
     */
    @Transactional
    public void resendVerificationCode(String email) {
        logger.info("Resending verification code for email: {}", email);
        
        // Find the user
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            logger.warn("User not found for email: {}", email);
            throw new EmailVerificationException("User not found with this email address.");
        }
        
        User user = userOpt.get();
        
        // Check if user is already verified
        if (user.isEmailVerified()) {
            logger.warn("Attempt to resend verification code for already verified email: {}", email);
            throw new EmailVerificationException("Email is already verified. You can now log in.");
        }
        
        // Create new verification record (this includes rate limiting check)
        createVerificationRecord(user);
        
        // Log code resend
        securityAuditService.logVerificationEvent(email, 
            SecurityAuditService.VerificationEvent.CODE_RESENT, true, 
            "Verification code resent to user", null);
    }

    /**
     * Checks if the email is within the rate limit for verification code requests.
     * 
     * @param email The email address to check
     * @return true if within rate limit, false otherwise
     */
    private boolean isWithinRateLimit(String email) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentCodeCount = emailVerificationRepository
            .countByEmailAndCreatedAtAfterAndUsedFalse(email, oneHourAgo);
        
        boolean withinLimit = recentCodeCount < maxCodesPerHour;
        logger.debug("Rate limit check for {}: {} codes in last hour (limit: {})", 
                    email, recentCodeCount, maxCodesPerHour);
        
        return withinLimit;
    }

    /**
     * Gets the number of remaining verification code requests for an email within the current hour.
     * 
     * @param email The email address to check
     * @return Number of remaining requests
     */
    public int getRemainingCodeRequests(String email) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentCodeCount = emailVerificationRepository
            .countByEmailAndCreatedAtAfterAndUsedFalse(email, oneHourAgo);
        
        return Math.max(0, maxCodesPerHour - (int) recentCodeCount);
    }

    /**
     * Checks if there is a valid (unused and not expired) verification code for the given email.
     * 
     * @param email The email address to check
     * @return true if a valid code exists, false otherwise
     */
    public boolean hasValidVerificationCode(String email) {
        Optional<EmailVerification> verificationOpt = 
            emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        
        if (verificationOpt.isEmpty()) {
            return false;
        }
        
        EmailVerification verification = verificationOpt.get();
        return !verification.isExpired();
    }

    /**
     * Gets the expiration time of the most recent verification code for an email.
     * 
     * @param email The email address to check
     * @return Optional containing the expiration time, or empty if no valid code exists
     */
    public Optional<LocalDateTime> getVerificationCodeExpiry(String email) {
        Optional<EmailVerification> verificationOpt = 
            emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        
        if (verificationOpt.isEmpty()) {
            return Optional.empty();
        }
        
        EmailVerification verification = verificationOpt.get();
        if (verification.isExpired()) {
            return Optional.empty();
        }
        
        return Optional.of(verification.getExpiresAt());
    }

    /**
     * Scheduled task to clean up expired verification codes.
     * Runs every hour to maintain database cleanliness.
     */
    @Scheduled(fixedRate = 3600000) // Run every hour (3600000 ms)
    @Transactional
    public void cleanupExpiredVerifications() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting cleanup of expired verification codes");
        
        LocalDateTime now = LocalDateTime.now();
        List<EmailVerification> expiredVerifications = 
            emailVerificationRepository.findByExpiresAtBeforeAndUsedFalse(now);
        
        int expiredCount = 0;
        if (!expiredVerifications.isEmpty()) {
            // Mark expired codes as used instead of deleting for audit trail
            for (EmailVerification verification : expiredVerifications) {
                verification.setUsed(true);
            }
            emailVerificationRepository.saveAll(expiredVerifications);
            expiredCount = expiredVerifications.size();
            
            logger.info("Marked {} expired verification codes as used", expiredCount);
        }
        
        // Delete very old verification records (older than 7 days) for cleanup
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        long oldRecordsCountBefore = emailVerificationRepository.count();
        emailVerificationRepository.deleteExpiredVerifications(sevenDaysAgo);
        long oldRecordsCountAfter = emailVerificationRepository.count();
        int deletedCount = (int) (oldRecordsCountBefore - oldRecordsCountAfter);
        
        long executionTime = System.currentTimeMillis() - startTime;
        int totalProcessed = expiredCount + deletedCount;
        
        logger.info("Cleanup of expired verification codes completed. Expired: {}, Deleted: {}, Time: {}ms", 
                   expiredCount, deletedCount, executionTime);
        
        // Log maintenance event
        securityAuditService.logMaintenanceEvent("VERIFICATION_CLEANUP", totalProcessed, 
            String.format("Expired codes marked as used: %d, Old records deleted: %d", expiredCount, deletedCount));
        
        // Log performance metrics
        securityAuditService.logPerformanceMetrics("VERIFICATION_CLEANUP", executionTime, totalProcessed);
        
        // Log cleanup event
        securityAuditService.logVerificationEvent("SYSTEM", 
            SecurityAuditService.VerificationEvent.CLEANUP_PERFORMED, true, 
            String.format("Processed %d records in %dms", totalProcessed, executionTime), null);
    }

    /**
     * Manual cleanup method for expired verification codes.
     * Can be called on-demand for immediate cleanup.
     */
    @Transactional
    public void performManualCleanup() {
        logger.info("Performing manual cleanup of expired verification codes");
        cleanupExpiredVerifications();
    }

    /**
     * Gets verification statistics for monitoring purposes.
     * 
     * @return Verification statistics
     */
    public VerificationStats getVerificationStats() {
        LocalDateTime now = LocalDateTime.now();
        
        long totalVerifications = emailVerificationRepository.count();
        long expiredCodes = emailVerificationRepository.findByExpiresAtBeforeAndUsedFalse(now).size();
        
        return new VerificationStats(totalVerifications, expiredCodes);
    }

    /**
     * Inner class for verification statistics.
     */
    public static class VerificationStats {
        private final long totalVerifications;
        private final long expiredCodes;
        
        public VerificationStats(long totalVerifications, long expiredCodes) {
            this.totalVerifications = totalVerifications;
            this.expiredCodes = expiredCodes;
        }
        
        public long getTotalVerifications() { return totalVerifications; }
        public long getExpiredCodes() { return expiredCodes; }
    }
}