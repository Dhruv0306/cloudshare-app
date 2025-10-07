package com.cloud.computing.filesharingapp.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Entity representing email notifications sent for file shares.
 * 
 * <p>This entity tracks email notifications sent to recipients when files
 * are shared with them, including:
 * <ul>
 *   <li>Recipient email address</li>
 *   <li>Delivery timestamp and status</li>
 *   <li>Unique notification identifier for tracking</li>
 *   <li>Reference to the file share being notified about</li>
 * </ul>
 * 
 * <p>This information is used for delivery confirmation, retry mechanisms,
 * and notification history tracking.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "share_notifications", indexes = {
    @Index(name = "idx_share_notification", columnList = "share_id, sent_at"),
    @Index(name = "idx_recipient_email", columnList = "recipient_email"),
    @Index(name = "idx_notification_id", columnList = "notification_id", unique = true)
})
public class ShareNotification {
    /** Unique identifier for the notification record */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The file share this notification is about */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    @JsonBackReference
    @NotNull
    private FileShare fileShare;

    /** Email address of the notification recipient */
    @NotBlank
    @Size(max = 100)
    @Email
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    /** Timestamp when the notification was sent */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** Whether the notification was successfully delivered */
    @Column(nullable = false)
    private boolean delivered = false;

    /** Unique identifier for tracking the notification (UUID-based) */
    @Size(max = 36)
    @Column(name = "notification_id", unique = true)
    private String notificationId;

    /**
     * Default constructor that initializes the sent timestamp.
     */
    public ShareNotification() {
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new share notification.
     * 
     * @param fileShare the file share being notified about
     * @param recipientEmail the email address of the recipient
     * @param notificationId unique identifier for tracking
     */
    public ShareNotification(FileShare fileShare, String recipientEmail, String notificationId) {
        this.fileShare = fileShare;
        this.recipientEmail = recipientEmail;
        this.notificationId = notificationId;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Marks the notification as successfully delivered.
     * 
     * <p>This method should be called when email delivery confirmation
     * is received from the email service provider. It updates the delivery
     * status to prevent retry attempts and maintain accurate delivery statistics.
     */
    public void markAsDelivered() {
        this.delivered = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileShare getFileShare() {
        return fileShare;
    }

    public void setFileShare(FileShare fileShare) {
        this.fileShare = fileShare;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }
}