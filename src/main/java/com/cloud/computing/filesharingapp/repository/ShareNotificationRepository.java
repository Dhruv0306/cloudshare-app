package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ShareNotification database operations.
 * 
 * <p>This repository provides data access methods for share notification entities
 * with support for delivery tracking, retry mechanisms, and notification history.
 * It extends JpaRepository to provide standard CRUD operations plus custom query methods.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Notification history by share</li>
 *   <li>Delivery status tracking</li>
 *   <li>Failed notification identification</li>
 *   <li>Recipient-based queries</li>
 *   <li>Notification ID lookup for tracking</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface ShareNotificationRepository extends JpaRepository<ShareNotification, Long> {
    
    /**
     * Finds all notifications for a specific file share, ordered by sent time.
     * 
     * @param fileShare the file share whose notifications to retrieve
     * @return List of ShareNotification objects for the share, newest first
     */
    List<ShareNotification> findByFileShareOrderBySentAtDesc(FileShare fileShare);
    
    /**
     * Finds notifications by their unique notification ID.
     * 
     * @param notificationId the unique notification identifier
     * @return Optional containing the ShareNotification if found
     */
    Optional<ShareNotification> findByNotificationId(String notificationId);
    
    /**
     * Finds all notifications sent to a specific email address.
     * 
     * @param recipientEmail the email address to search for
     * @return List of ShareNotification objects sent to the email
     */
    List<ShareNotification> findByRecipientEmailOrderBySentAtDesc(String recipientEmail);
    
    /**
     * Finds notifications for a specific share and recipient.
     * 
     * @param fileShare the file share to query
     * @param recipientEmail the recipient email to filter by
     * @return List of ShareNotification objects matching the criteria
     */
    List<ShareNotification> findByFileShareAndRecipientEmailOrderBySentAtDesc(FileShare fileShare, String recipientEmail);
    
    /**
     * Finds all undelivered notifications.
     * 
     * @return List of ShareNotification objects that were not delivered
     */
    List<ShareNotification> findByDeliveredFalseOrderBySentAtAsc();
    
    /**
     * Finds undelivered notifications older than a specific time.
     * 
     * @param sentBefore the cutoff time for old notifications
     * @return List of old undelivered ShareNotification objects
     */
    List<ShareNotification> findByDeliveredFalseAndSentAtBeforeOrderBySentAtAsc(LocalDateTime sentBefore);
    
    /**
     * Finds delivered notifications for a specific file share.
     * 
     * @param fileShare the file share to query
     * @return List of successfully delivered ShareNotification objects
     */
    List<ShareNotification> findByFileShareAndDeliveredTrueOrderBySentAtDesc(FileShare fileShare);
    
    /**
     * Counts the total number of notifications for a file share.
     * 
     * @param fileShare the file share to count notifications for
     * @return the total number of notifications sent
     */
    long countByFileShare(FileShare fileShare);
    
    /**
     * Counts delivered notifications for a file share.
     * 
     * @param fileShare the file share to query
     * @return the number of successfully delivered notifications
     */
    long countByFileShareAndDeliveredTrue(FileShare fileShare);
    
    /**
     * Counts undelivered notifications for a file share.
     * 
     * @param fileShare the file share to query
     * @return the number of undelivered notifications
     */
    long countByFileShareAndDeliveredFalse(FileShare fileShare);
    
    /**
     * Counts notifications sent to a specific email address.
     * 
     * @param recipientEmail the email address to count notifications for
     * @return the number of notifications sent to the email
     */
    long countByRecipientEmail(String recipientEmail);
    
    /**
     * Gets notification statistics by delivery status within a time period.
     * 
     * <p>This method provides analytics on email notification delivery success rates
     * within a specified time range. Useful for monitoring email service health
     * and identifying delivery issues.
     * 
     * <p>Returns an array where:
     * <ul>
     *   <li>Index 0: Delivery status (Boolean - true for delivered, false for failed)</li>
     *   <li>Index 1: Count of notifications (Long)</li>
     * </ul>
     * 
     * @param since the start of the time period (inclusive)
     * @param until the end of the time period (inclusive)
     * @return List of Object arrays containing delivery status and counts
     */
    @Query("SELECT sn.delivered, COUNT(sn) FROM ShareNotification sn " +
           "WHERE sn.sentAt BETWEEN :since AND :until " +
           "GROUP BY sn.delivered")
    List<Object[]> getDeliveryStatsByPeriod(@Param("since") LocalDateTime since, 
                                            @Param("until") LocalDateTime until);
    
    /**
     * Gets daily notification counts within a date range.
     * 
     * @param since the start date
     * @param until the end date
     * @return List of daily notification counts
     */
    @Query("SELECT DATE(sn.sentAt), COUNT(sn) FROM ShareNotification sn " +
           "WHERE sn.sentAt BETWEEN :since AND :until " +
           "GROUP BY DATE(sn.sentAt) ORDER BY DATE(sn.sentAt)")
    List<Object[]> getDailyNotificationCounts(@Param("since") LocalDateTime since, 
                                              @Param("until") LocalDateTime until);
    
    /**
     * Finds notifications that need retry (undelivered and within retry window).
     * 
     * @param maxRetryAge the maximum age for retry attempts
     * @return List of ShareNotification objects eligible for retry
     */
    @Query("SELECT sn FROM ShareNotification sn " +
           "WHERE sn.delivered = false AND sn.sentAt > :maxRetryAge " +
           "ORDER BY sn.sentAt ASC")
    List<ShareNotification> findNotificationsForRetry(@Param("maxRetryAge") LocalDateTime maxRetryAge);
    
    /**
     * Gets the most recent notifications across all shares for monitoring.
     * 
     * @param limit the maximum number of notifications to return
     * @return List of the most recent ShareNotification objects
     */
    @Query("SELECT sn FROM ShareNotification sn ORDER BY sn.sentAt DESC")
    List<ShareNotification> findRecentNotifications(@Param("limit") int limit);
}