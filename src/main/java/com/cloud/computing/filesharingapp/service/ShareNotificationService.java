package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareNotification;
import com.cloud.computing.filesharingapp.repository.ShareNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for managing email notifications for file shares.
 * 
 * <p>This service handles sending email notifications when files are shared,
 * including delivery tracking, retry mechanisms, and notification history.
 * It integrates with the existing EmailService infrastructure for consistent
 * email handling across the application.
 * 
 * <p>Key features:
 * <ul>
 *   <li>HTML email templates for share notifications</li>
 *   <li>Email validation and delivery status tracking</li>
 *   <li>Notification history and retry mechanisms</li>
 *   <li>Integration with existing email configuration</li>
 *   <li>Bulk notification support for multiple recipients</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Transactional
public class ShareNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(ShareNotificationService.class);
    
    // Email validation pattern (RFC 5322 compliant)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    // Date formatter for email templates
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");

    @Autowired
    private ShareNotificationRepository shareNotificationRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.verification.from-email}")
    private String fromEmail;

    @Value("${app.verification.from-name}")
    private String fromName;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Sends share notification emails to a list of recipients.
     * 
     * <p>This method validates email addresses, creates notification records,
     * and sends HTML emails with share details and access links. Each email
     * is tracked individually for delivery status and retry purposes.
     * 
     * @param fileShare the file share to notify about
     * @param recipientEmails list of email addresses to notify
     * @throws IllegalArgumentException if fileShare is null or recipientEmails is empty
     * @throws RuntimeException if critical email sending failures occur
     */
    public void sendShareNotifications(@NotNull FileShare fileShare, @NotNull List<@Email String> recipientEmails) {
        if (fileShare == null) {
            throw new IllegalArgumentException("FileShare cannot be null");
        }
        
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            throw new IllegalArgumentException("Recipient emails list cannot be null or empty");
        }

        logger.info("Sending share notifications for file share ID: {} to {} recipients", 
                   fileShare.getId(), recipientEmails.size());

        int successCount = 0;
        int failureCount = 0;

        for (String email : recipientEmails) {
            try {
                if (isValidEmail(email)) {
                    sendSingleShareNotification(fileShare, email);
                    successCount++;
                    logger.debug("Successfully sent share notification to: {}", email);
                } else {
                    logger.warn("Invalid email address skipped: {}", email);
                    failureCount++;
                }
            } catch (Exception ex) {
                logger.error("Failed to send share notification to: {} - {}", email, ex.getMessage(), ex);
                failureCount++;
            }
        }

        logger.info("Share notification batch completed for file share ID: {} - Success: {}, Failures: {}", 
                   fileShare.getId(), successCount, failureCount);
    }

    /**
     * Sends a share notification email to a single recipient.
     * 
     * @param fileShare the file share to notify about
     * @param recipientEmail the email address to send notification to
     * @throws RuntimeException if email sending fails
     */
    public void sendSingleShareNotification(@NotNull FileShare fileShare, @NotBlank @Email String recipientEmail) {
        if (!isValidEmail(recipientEmail)) {
            throw new IllegalArgumentException("Invalid email address: " + recipientEmail);
        }

        String notificationId = UUID.randomUUID().toString();
        ShareNotification notification = new ShareNotification(fileShare, recipientEmail, notificationId);

        try {
            // Save notification record first
            shareNotificationRepository.save(notification);
            
            // Send the email
            sendShareNotificationEmail(fileShare, recipientEmail, notificationId);
            
            // Mark as delivered on successful send
            notification.markAsDelivered();
            shareNotificationRepository.save(notification);
            
            logger.info("Share notification sent successfully to: {} for file share ID: {}", 
                       recipientEmail, fileShare.getId());
            
        } catch (Exception ex) {
            logger.error("Failed to send share notification to: {} for file share ID: {} - {}", 
                        recipientEmail, fileShare.getId(), ex.getMessage(), ex);
            
            // Notification record remains with delivered=false for retry
            throw new RuntimeException("Failed to send share notification", ex);
        }
    }

    /**
     * Sends share revocation notification emails to recipients.
     * 
     * @param fileShare the file share that was revoked
     * @param recipientEmails list of email addresses to notify about revocation
     */
    public void sendShareRevokedNotifications(@NotNull FileShare fileShare, @NotNull List<@Email String> recipientEmails) {
        if (fileShare == null) {
            throw new IllegalArgumentException("FileShare cannot be null");
        }
        
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            logger.debug("No recipients specified for share revocation notification");
            return;
        }

        logger.info("Sending share revocation notifications for file share ID: {} to {} recipients", 
                   fileShare.getId(), recipientEmails.size());

        for (String email : recipientEmails) {
            try {
                if (isValidEmail(email)) {
                    sendShareRevokedNotificationEmail(fileShare, email);
                    logger.debug("Successfully sent share revocation notification to: {}", email);
                } else {
                    logger.warn("Invalid email address skipped for revocation notification: {}", email);
                }
            } catch (Exception ex) {
                logger.error("Failed to send share revocation notification to: {} - {}", email, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Gets notification history for a specific file share.
     * 
     * @param fileShare the file share to get notifications for
     * @return list of ShareNotification objects ordered by sent time (newest first)
     */
    public List<ShareNotification> getNotificationHistory(@NotNull FileShare fileShare) {
        return shareNotificationRepository.findByFileShareOrderBySentAtDesc(fileShare);
    }

    /**
     * Gets notification statistics for a file share.
     * 
     * @param fileShare the file share to get statistics for
     * @return ShareNotificationStats object with delivery statistics
     */
    public ShareNotificationStats getNotificationStats(@NotNull FileShare fileShare) {
        long totalNotifications = shareNotificationRepository.countByFileShare(fileShare);
        long deliveredNotifications = shareNotificationRepository.countByFileShareAndDeliveredTrue(fileShare);
        long failedNotifications = shareNotificationRepository.countByFileShareAndDeliveredFalse(fileShare);
        
        return new ShareNotificationStats(totalNotifications, deliveredNotifications, failedNotifications);
    }

    /**
     * Retries failed notification deliveries.
     * 
     * <p>This method finds undelivered notifications within the retry window
     * and attempts to resend them. Notifications older than the retry window
     * are considered permanently failed.
     * 
     * @param maxRetryHours maximum age in hours for retry attempts
     * @return number of notifications successfully retried
     */
    public int retryFailedNotifications(int maxRetryHours) {
        LocalDateTime maxRetryAge = LocalDateTime.now().minusHours(maxRetryHours);
        List<ShareNotification> failedNotifications = shareNotificationRepository.findNotificationsForRetry(maxRetryAge);
        
        if (failedNotifications.isEmpty()) {
            logger.debug("No failed notifications found for retry");
            return 0;
        }

        logger.info("Retrying {} failed notifications", failedNotifications.size());
        
        int successCount = 0;
        for (ShareNotification notification : failedNotifications) {
            try {
                sendShareNotificationEmail(notification.getFileShare(), 
                                         notification.getRecipientEmail(), 
                                         notification.getNotificationId());
                
                notification.markAsDelivered();
                shareNotificationRepository.save(notification);
                successCount++;
                
                logger.debug("Successfully retried notification to: {}", notification.getRecipientEmail());
                
            } catch (Exception ex) {
                logger.error("Failed to retry notification to: {} - {}", 
                           notification.getRecipientEmail(), ex.getMessage(), ex);
            }
        }
        
        logger.info("Retry completed: {} out of {} notifications successfully resent", 
                   successCount, failedNotifications.size());
        
        return successCount;
    }

    /**
     * Validates an email address format.
     * 
     * @param email the email address to validate
     * @return true if the email format is valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        return email != null && !email.trim().isEmpty() && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Sends the actual share notification email.
     * 
     * @param fileShare the file share to notify about
     * @param recipientEmail the recipient email address
     * @param notificationId unique identifier for tracking
     * @throws MessagingException if email creation fails
     * @throws MailException if email sending fails
     * @throws UnsupportedEncodingException if encoding fails
     */
    private void sendShareNotificationEmail(FileShare fileShare, String recipientEmail, String notificationId) 
            throws MessagingException, MailException, UnsupportedEncodingException {
        
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail, fromName);
        helper.setTo(recipientEmail);
        helper.setSubject(buildShareNotificationSubject(fileShare));
        
        String htmlContent = buildShareNotificationTemplate(fileShare, notificationId);
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }

    /**
     * Sends share revocation notification email.
     * 
     * @param fileShare the revoked file share
     * @param recipientEmail the recipient email address
     * @throws MessagingException if email creation fails
     * @throws MailException if email sending fails
     * @throws UnsupportedEncodingException if encoding fails
     */
    private void sendShareRevokedNotificationEmail(FileShare fileShare, String recipientEmail) 
            throws MessagingException, MailException, UnsupportedEncodingException {
        
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail, fromName);
        helper.setTo(recipientEmail);
        helper.setSubject("File Share Access Revoked - " + fileShare.getFile().getFileName());
        
        String htmlContent = buildShareRevokedTemplate(fileShare);
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }

    /**
     * Builds the subject line for share notification emails.
     * 
     * @param fileShare the file share being notified about
     * @return formatted subject line
     */
    private String buildShareNotificationSubject(FileShare fileShare) {
        String ownerName = fileShare.getOwner().getUsername();
        String fileName = fileShare.getFile().getFileName();
        return String.format("%s shared a file with you: %s", ownerName, fileName);
    }    /**

     * Builds the HTML template for share notification emails.
     * 
     * @param fileShare the file share being notified about
     * @param notificationId unique identifier for tracking
     * @return HTML email content
     */
    private String buildShareNotificationTemplate(FileShare fileShare, String notificationId) {
        String ownerName = fileShare.getOwner().getUsername();
        String fileName = fileShare.getFile().getFileName();
        String fileSize = formatFileSize(fileShare.getFile().getFileSize());
        String shareUrl = baseUrl + "/shared/" + fileShare.getShareToken();
        String permissionText = fileShare.getPermission().name().equals("DOWNLOAD") ? "view and download" : "view only";
        String expirationText = fileShare.getExpiresAt() != null ? 
            fileShare.getExpiresAt().format(DATE_FORMATTER) : "No expiration";
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>File Shared With You</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #007bff; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { padding: 30px 20px; background-color: #f8f9fa; border-radius: 0 0 8px 8px; }
                    .file-info { 
                        background-color: white; 
                        padding: 20px; 
                        border-radius: 8px; 
                        margin: 20px 0; 
                        border-left: 4px solid #007bff; 
                    }
                    .file-name { font-size: 18px; font-weight: bold; color: #007bff; margin-bottom: 10px; }
                    .file-details { color: #666; font-size: 14px; }
                    .access-button { 
                        display: inline-block; 
                        background-color: #007bff; 
                        color: white; 
                        padding: 12px 24px; 
                        text-decoration: none; 
                        border-radius: 5px; 
                        font-weight: bold; 
                        margin: 20px 0; 
                    }
                    .access-button:hover { background-color: #0056b3; }
                    .share-link { 
                        background-color: #e9ecef; 
                        padding: 10px; 
                        border-radius: 4px; 
                        font-family: monospace; 
                        word-break: break-all; 
                        margin: 10px 0; 
                    }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                    .warning { color: #dc3545; font-weight: bold; margin: 15px 0; }
                    .info-row { margin: 8px 0; }
                    .info-label { font-weight: bold; display: inline-block; width: 100px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📁 File Sharing App</h1>
                        <h2>%s shared a file with you!</h2>
                    </div>
                    <div class="content">
                        <p>Hello!</p>
                        <p><strong>%s</strong> has shared a file with you through File Sharing App. You can access it using the details below:</p>
                        
                        <div class="file-info">
                            <div class="file-name">📄 %s</div>
                            <div class="file-details">
                                <div class="info-row">
                                    <span class="info-label">File Size:</span> %s
                                </div>
                                <div class="info-row">
                                    <span class="info-label">Permission:</span> %s
                                </div>
                                <div class="info-row">
                                    <span class="info-label">Expires:</span> %s
                                </div>
                                <div class="info-row">
                                    <span class="info-label">Shared by:</span> %s
                                </div>
                            </div>
                        </div>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="access-button">🔗 Access File</a>
                        </div>
                        
                        <p><strong>Direct Link:</strong></p>
                        <div class="share-link">%s</div>
                        
                        <p><strong>Instructions:</strong></p>
                        <ul>
                            <li>Click the "Access File" button above or copy the direct link</li>
                            <li>You can %s the file based on the permissions granted</li>
                            <li>This link will expire on: %s</li>
                            <li>No account registration is required to access the file</li>
                        </ul>
                        
                        <div class="warning">
                            ⚠️ Security Notice: Only share this link with people you trust. Anyone with this link can access the file according to the permissions set.
                        </div>
                        
                        <p>If you have any questions about this shared file, please contact <strong>%s</strong> directly.</p>
                        
                        <p>Best regards,<br>The File Sharing App Team</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message. Please do not reply to this email.</p>
                        <p>Notification ID: %s</p>
                        <p>© 2025 File Sharing App. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                ownerName, ownerName, fileName, fileSize, permissionText, expirationText, ownerName,
                shareUrl, shareUrl, permissionText, expirationText, ownerName, notificationId
            );
    }

    /**
     * Builds the HTML template for share revocation notification emails.
     * 
     * @param fileShare the revoked file share
     * @return HTML email content
     */
    private String buildShareRevokedTemplate(FileShare fileShare) {
        String ownerName = fileShare.getOwner().getUsername();
        String fileName = fileShare.getFile().getFileName();
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>File Share Access Revoked</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #dc3545; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { padding: 30px 20px; background-color: #f8f9fa; border-radius: 0 0 8px 8px; }
                    .file-info { 
                        background-color: white; 
                        padding: 20px; 
                        border-radius: 8px; 
                        margin: 20px 0; 
                        border-left: 4px solid #dc3545; 
                    }
                    .file-name { font-size: 18px; font-weight: bold; color: #dc3545; margin-bottom: 10px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                    .notice { color: #dc3545; font-weight: bold; margin: 15px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📁 File Sharing App</h1>
                        <h2>File Share Access Revoked</h2>
                    </div>
                    <div class="content">
                        <p>Hello!</p>
                        <p>This is to inform you that access to a previously shared file has been revoked by the owner.</p>
                        
                        <div class="file-info">
                            <div class="file-name">📄 %s</div>
                            <p><strong>Shared by:</strong> %s</p>
                        </div>
                        
                        <div class="notice">
                            🚫 The share link for this file is no longer active and cannot be used to access the file.
                        </div>
                        
                        <p>If you need continued access to this file, please contact <strong>%s</strong> directly to request a new share link.</p>
                        
                        <p>Thank you for using File Sharing App.</p>
                        
                        <p>Best regards,<br>The File Sharing App Team</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message. Please do not reply to this email.</p>
                        <p>© 2025 File Sharing App. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(fileName, ownerName, ownerName);
    }

    /**
     * Formats file size in human-readable format.
     * 
     * @param bytes file size in bytes
     * @return formatted file size string
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Statistics class for share notifications.
     */
    public static class ShareNotificationStats {
        private final long totalNotifications;
        private final long deliveredNotifications;
        private final long failedNotifications;

        public ShareNotificationStats(long totalNotifications, long deliveredNotifications, long failedNotifications) {
            this.totalNotifications = totalNotifications;
            this.deliveredNotifications = deliveredNotifications;
            this.failedNotifications = failedNotifications;
        }

        public long getTotalNotifications() {
            return totalNotifications;
        }

        public long getDeliveredNotifications() {
            return deliveredNotifications;
        }

        public long getFailedNotifications() {
            return failedNotifications;
        }

        public double getDeliveryRate() {
            return totalNotifications > 0 ? (double) deliveredNotifications / totalNotifications * 100 : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ShareNotificationStats{total=%d, delivered=%d, failed=%d, deliveryRate=%.1f%%}", 
                               totalNotifications, deliveredNotifications, failedNotifications, getDeliveryRate());
        }
    }
}