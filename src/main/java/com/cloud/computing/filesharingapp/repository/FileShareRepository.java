package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for FileShare database operations.
 * 
 * <p>This repository provides data access methods for file share entities with
 * support for token-based lookups, user-scoped queries, and maintenance operations.
 * It extends JpaRepository to provide standard CRUD operations plus custom query methods.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Share lookup by unique token</li>
 *   <li>User-scoped share queries for management</li>
 *   <li>File-specific share queries</li>
 *   <li>Expired share cleanup operations</li>
 *   <li>Active share filtering</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface FileShareRepository extends JpaRepository<FileShare, Long> {
    
    /**
     * Finds an active file share by its unique token.
     * 
     * @param shareToken the share token to search for
     * @return Optional containing the FileShare if found and active
     */
    Optional<FileShare> findByShareTokenAndActiveTrue(String shareToken);
    
    /**
     * Finds a file share by its unique token (regardless of active status).
     * 
     * @param shareToken the share token to search for
     * @return Optional containing the FileShare if found
     */
    Optional<FileShare> findByShareToken(String shareToken);
    
    /**
     * Finds all active shares belonging to a specific user.
     * 
     * @param owner the user whose shares to retrieve
     * @return List of active FileShare objects owned by the user
     */
    List<FileShare> findByOwnerAndActiveTrueOrderByCreatedAtDesc(User owner);
    
    /**
     * Finds all shares (active and inactive) belonging to a specific user.
     * 
     * @param owner the user whose shares to retrieve
     * @return List of all FileShare objects owned by the user
     */
    List<FileShare> findByOwnerOrderByCreatedAtDesc(User owner);
    
    /**
     * Finds all active shares for a specific file.
     * 
     * @param file the file whose shares to retrieve
     * @return List of active FileShare objects for the file
     */
    List<FileShare> findByFileAndActiveTrueOrderByCreatedAtDesc(FileEntity file);
    
    /**
     * Finds all shares (active and inactive) for a specific file.
     * 
     * @param file the file whose shares to retrieve
     * @return List of all FileShare objects for the file
     */
    List<FileShare> findByFileOrderByCreatedAtDesc(FileEntity file);
    
    /**
     * Finds a specific share by ID that belongs to a user.
     * 
     * @param id the share ID to search for
     * @param owner the user who should own the share
     * @return Optional containing the FileShare if found and owned by user
     */
    Optional<FileShare> findByIdAndOwner(Long id, User owner);
    
    /**
     * Finds all shares that have expired but are still marked as active.
     * 
     * <p>This method is useful for cleanup operations to identify shares that
     * should be deactivated due to expiration. Only shares with a set expiration
     * time are considered.
     * 
     * @param currentTime the current timestamp to compare against
     * @return List of expired but active FileShare objects
     */
    @Query("SELECT fs FROM FileShare fs WHERE fs.active = true AND fs.expiresAt IS NOT NULL AND fs.expiresAt < :currentTime")
    List<FileShare> findExpiredActiveShares(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Finds all shares that have reached their maximum access count but are still active.
     * 
     * <p>This method identifies shares that should be deactivated because they have
     * reached their configured access limit. Only shares with a set maximum access
     * count are considered.
     * 
     * @return List of FileShare objects that have exceeded their access limit
     */
    @Query("SELECT fs FROM FileShare fs WHERE fs.active = true AND fs.maxAccess IS NOT NULL AND fs.accessCount >= fs.maxAccess")
    List<FileShare> findMaxAccessReachedShares();
    
    /**
     * Deactivates all shares for a specific file.
     * 
     * @param file the file whose shares should be deactivated
     * @return the number of shares that were deactivated
     */
    @Modifying
    @Query("UPDATE FileShare fs SET fs.active = false WHERE fs.file = :file")
    int deactivateSharesForFile(@Param("file") FileEntity file);
    
    /**
     * Deactivates expired shares.
     * 
     * @param currentTime the current timestamp to compare against
     * @return the number of shares that were deactivated
     */
    @Modifying
    @Query("UPDATE FileShare fs SET fs.active = false WHERE fs.active = true AND fs.expiresAt IS NOT NULL AND fs.expiresAt < :currentTime")
    int deactivateExpiredShares(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Deactivates shares that have reached their maximum access count.
     * 
     * @return the number of shares that were deactivated
     */
    @Modifying
    @Query("UPDATE FileShare fs SET fs.active = false WHERE fs.active = true AND fs.maxAccess IS NOT NULL AND fs.accessCount >= fs.maxAccess")
    int deactivateMaxAccessReachedShares();
    
    /**
     * Counts the number of active shares for a specific user.
     * 
     * @param owner the user whose active shares to count
     * @return the number of active shares owned by the user
     */
    long countByOwnerAndActiveTrue(User owner);
    
    /**
     * Counts the number of active shares for a specific file.
     * 
     * @param file the file whose active shares to count
     * @return the number of active shares for the file
     */
    long countByFileAndActiveTrue(FileEntity file);
    
    /**
     * Counts the number of active shares in the system.
     * 
     * @return the total number of active shares
     */
    long countByActiveTrue();
    
    /**
     * Counts shares created after a specific date.
     * 
     * @param since the earliest creation date to include
     * @return the number of shares created since the date
     */
    long countByCreatedAtAfter(LocalDateTime since);
    
    /**
     * Finds active shares ordered by creation date (newest first).
     * 
     * @return List of active FileShare objects ordered by creation date
     */
    List<FileShare> findByActiveTrueOrderByCreatedAtDesc();
    
    /**
     * Counts shares created by a specific user after a given date.
     * 
     * @param owner the user whose shares to count
     * @param since the earliest creation date to include
     * @return the number of shares created by the user since the date
     */
    long countByOwnerAndCreatedAtAfter(User owner, LocalDateTime since);
    
    /**
     * Counts active shares created after a specific date.
     * 
     * @param since the earliest creation date to include
     * @return the number of active shares created since the date
     */
    long countByActiveTrueAndCreatedAtAfter(LocalDateTime since);
}