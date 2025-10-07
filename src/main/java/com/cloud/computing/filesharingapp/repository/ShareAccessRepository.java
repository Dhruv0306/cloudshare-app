package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccess;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for ShareAccess database operations.
 * 
 * <p>This repository provides data access methods for share access log entities
 * with support for analytics, security monitoring, and access tracking.
 * It extends JpaRepository to provide standard CRUD operations plus custom query methods.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Access history tracking by share</li>
 *   <li>IP-based access monitoring</li>
 *   <li>Time-based access analytics</li>
 *   <li>Access type filtering (view vs download)</li>
 *   <li>Rate limiting support queries</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface ShareAccessRepository extends JpaRepository<ShareAccess, Long> {
    
    /**
     * Finds all access logs for a specific file share, ordered by access time.
     * 
     * @param fileShare the file share whose access logs to retrieve
     * @return List of ShareAccess objects for the share, newest first
     */
    List<ShareAccess> findByFileShareOrderByAccessedAtDesc(FileShare fileShare);
    
    /**
     * Finds access logs for a specific file share and access type.
     * 
     * @param fileShare the file share to query
     * @param accessType the type of access to filter by
     * @return List of ShareAccess objects matching the criteria
     */
    List<ShareAccess> findByFileShareAndAccessTypeOrderByAccessedAtDesc(FileShare fileShare, ShareAccessType accessType);
    
    /**
     * Finds recent access logs from a specific IP address.
     * 
     * @param accessorIp the IP address to search for
     * @param since the earliest timestamp to include
     * @return List of ShareAccess objects from the IP since the given time
     */
    List<ShareAccess> findByAccessorIpAndAccessedAtAfterOrderByAccessedAtDesc(String accessorIp, LocalDateTime since);
    
    /**
     * Finds recent access logs for a specific share from a specific IP.
     * 
     * @param fileShare the file share to query
     * @param accessorIp the IP address to filter by
     * @param since the earliest timestamp to include
     * @return List of ShareAccess objects matching the criteria
     */
    List<ShareAccess> findByFileShareAndAccessorIpAndAccessedAtAfterOrderByAccessedAtDesc(
            FileShare fileShare, String accessorIp, LocalDateTime since);
    
    /**
     * Counts the total number of accesses for a specific file share.
     * 
     * @param fileShare the file share to count accesses for
     * @return the total number of access attempts
     */
    long countByFileShare(FileShare fileShare);
    
    /**
     * Counts accesses of a specific type for a file share.
     * 
     * @param fileShare the file share to query
     * @param accessType the type of access to count
     * @return the number of accesses of the specified type
     */
    long countByFileShareAndAccessType(FileShare fileShare, ShareAccessType accessType);
    
    /**
     * Counts recent accesses from a specific IP address.
     * 
     * @param accessorIp the IP address to count accesses for
     * @param since the earliest timestamp to include
     * @return the number of accesses from the IP since the given time
     */
    long countByAccessorIpAndAccessedAtAfter(String accessorIp, LocalDateTime since);
    
    /**
     * Counts recent accesses for a specific share from a specific IP.
     * 
     * @param fileShare the file share to query
     * @param accessorIp the IP address to filter by
     * @param since the earliest timestamp to include
     * @return the number of accesses matching the criteria
     */
    long countByFileShareAndAccessorIpAndAccessedAtAfter(FileShare fileShare, String accessorIp, LocalDateTime since);
    
    /**
     * Gets access statistics for a file share within a time period.
     * 
     * @param fileShare the file share to analyze
     * @param since the start of the time period
     * @param until the end of the time period
     * @return List of access counts grouped by access type
     */
    @Query("SELECT sa.accessType, COUNT(sa) FROM ShareAccess sa " +
           "WHERE sa.fileShare = :fileShare AND sa.accessedAt BETWEEN :since AND :until " +
           "GROUP BY sa.accessType")
    List<Object[]> getAccessStatsByTypeAndPeriod(@Param("fileShare") FileShare fileShare, 
                                                  @Param("since") LocalDateTime since, 
                                                  @Param("until") LocalDateTime until);
    
    /**
     * Gets daily access counts for a file share within a date range.
     * 
     * @param fileShare the file share to analyze
     * @param since the start date
     * @param until the end date
     * @return List of daily access counts
     */
    @Query("SELECT DATE(sa.accessedAt), COUNT(sa) FROM ShareAccess sa " +
           "WHERE sa.fileShare = :fileShare AND sa.accessedAt BETWEEN :since AND :until " +
           "GROUP BY DATE(sa.accessedAt) ORDER BY DATE(sa.accessedAt)")
    List<Object[]> getDailyAccessCounts(@Param("fileShare") FileShare fileShare, 
                                        @Param("since") LocalDateTime since, 
                                        @Param("until") LocalDateTime until);
    
    /**
     * Gets the most recent access logs across all shares for security monitoring.
     * 
     * @param limit the maximum number of logs to return
     * @return List of the most recent ShareAccess objects
     */
    @Query("SELECT sa FROM ShareAccess sa ORDER BY sa.accessedAt DESC")
    List<ShareAccess> findRecentAccesses(@Param("limit") int limit);
    
    /**
     * Finds suspicious access patterns (multiple accesses from same IP in short time).
     * 
     * @param accessorIp the IP address to check
     * @param since the time window to check
     * @param minCount the minimum number of accesses to be considered suspicious
     * @return List of IP addresses with suspicious access patterns
     */
    @Query("SELECT sa.accessorIp, COUNT(sa) FROM ShareAccess sa " +
           "WHERE sa.accessorIp = :accessorIp AND sa.accessedAt > :since " +
           "GROUP BY sa.accessorIp HAVING COUNT(sa) >= :minCount")
    List<Object[]> findSuspiciousAccessPatterns(@Param("accessorIp") String accessorIp, 
                                                 @Param("since") LocalDateTime since, 
                                                 @Param("minCount") long minCount);
}