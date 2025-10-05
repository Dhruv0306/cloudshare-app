package com.cloud.computing.filesharingapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service for sending emails, particularly verification emails for user registration.
 * Handles SMTP configuration and email template generation.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.verification.from-email}")
    private String fromEmail;

    @Value("${app.verification.from-name}")
    private String fromName;

    /**
     * Sends a verification email with the provided code to the specified email address.
     * 
     * @param email The recipient's email address
     * @param verificationCode The 6-digit verification code
     * @throws RuntimeException if email sending fails
     */
    public void sendVerificationEmail(String email, String verificationCode) {
        logger.info("Sending verification email to: {}", email);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, fromName);
            helper.setTo(email);
            helper.setSubject("Verify Your Email Address - File Sharing App");
            
            String htmlContent = buildVerificationEmailTemplate(verificationCode);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            logger.info("Verification email sent successfully to: {}", email);
            
        } catch (MailException ex) {
            logger.error("Failed to send verification email to: {} - {}", email, ex.getMessage(), ex);
            throw new RuntimeException("Failed to send verification email", ex);
        } catch (MessagingException ex) {
            logger.error("Failed to create verification email message for: {} - {}", email, ex.getMessage(), ex);
            throw new RuntimeException("Failed to create email message", ex);
        } catch (Exception ex) {
            logger.error("Unexpected error sending verification email to: {} - {}", email, ex.getMessage(), ex);
            throw new RuntimeException("Unexpected error sending verification email", ex);
        }
    }

    /**
     * Sends a simple text verification email (fallback method).
     * 
     * @param email The recipient's email address
     * @param verificationCode The 6-digit verification code
     */
    public void sendSimpleVerificationEmail(String email, String verificationCode) {
        logger.info("Sending simple verification email to: {}", email);
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Verify Your Email Address - File Sharing App");
            message.setText(buildSimpleVerificationEmailText(verificationCode));
            
            mailSender.send(message);
            logger.info("Simple verification email sent successfully to: {}", email);
            
        } catch (MailException ex) {
            logger.error("Failed to send simple verification email to: {} - {}", email, ex.getMessage(), ex);
            throw new RuntimeException("Failed to send verification email", ex);
        }
    }

    /**
     * Checks if the email service is available and properly configured.
     * 
     * @return true if email service is available, false otherwise
     */
    public boolean isEmailServiceAvailable() {
        try {
            // Test the mail sender configuration
            mailSender.createMimeMessage();
            logger.debug("Email service is available");
            return true;
        } catch (Exception ex) {
            logger.warn("Email service is not available: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Builds the HTML template for verification emails.
     * 
     * @param verificationCode The 6-digit verification code
     * @return HTML email content
     */
    private String buildVerificationEmailTemplate(String verificationCode) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Email Verification</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #007bff; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px 20px; background-color: #f8f9fa; }
                    .verification-code { 
                        font-size: 32px; 
                        font-weight: bold; 
                        color: #007bff; 
                        text-align: center; 
                        padding: 20px; 
                        background-color: white; 
                        border: 2px dashed #007bff; 
                        margin: 20px 0; 
                        letter-spacing: 5px;
                    }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                    .warning { color: #dc3545; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>File Sharing App</h1>
                        <h2>Email Verification</h2>
                    </div>
                    <div class="content">
                        <h3>Welcome to File Sharing App!</h3>
                        <p>Thank you for registering with us. To complete your account setup, please verify your email address using the verification code below:</p>
                        
                        <div class="verification-code">%s</div>
                        
                        <p><strong>Instructions:</strong></p>
                        <ul>
                            <li>Enter this code in the verification form on our website</li>
                            <li>This code will expire in 15 minutes</li>
                            <li>If you didn't request this verification, please ignore this email</li>
                        </ul>
                        
                        <p class="warning">⚠️ Do not share this code with anyone. Our team will never ask for your verification code.</p>
                        
                        <p>If you have any questions or need assistance, please contact our support team.</p>
                        
                        <p>Best regards,<br>The File Sharing App Team</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message. Please do not reply to this email.</p>
                        <p>© 2025 File Sharing App. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(verificationCode);
    }

    /**
     * Builds simple text content for verification emails (fallback).
     * 
     * @param verificationCode The 6-digit verification code
     * @return Plain text email content
     */
    private String buildSimpleVerificationEmailText(String verificationCode) {
        return """
            Welcome to File Sharing App!
            
            Thank you for registering with us. To complete your account setup, please verify your email address using the verification code below:
            
            Verification Code: %s
            
            Instructions:
            - Enter this code in the verification form on our website
            - This code will expire in 15 minutes
            - If you didn't request this verification, please ignore this email
            
            WARNING: Do not share this code with anyone. Our team will never ask for your verification code.
            
            If you have any questions or need assistance, please contact our support team.
            
            Best regards,
            The File Sharing App Team
            
            ---
            This is an automated message. Please do not reply to this email.
            © 2025 File Sharing App. All rights reserved.
            """.formatted(verificationCode);
    }
}