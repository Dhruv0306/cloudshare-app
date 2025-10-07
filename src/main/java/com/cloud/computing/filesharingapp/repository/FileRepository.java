package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for FileEntity database operations.
 * 
 * <p>This repository provides data access methods for file entities with
 * support for user-scoped queries to ensure proper access control. It extends
 * JpaRepository to provide standard CRUD operations plus custom query methods.
 * 
 * <p>Key features:
 * <ul>
 *   <li>File lookup by stored filename</li>
 *   <li>User-scoped file queries for access control</li>
 *   <li>Combined ID and owner validation</li>
 *   <li>Automatic query generation from method names</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {
    
    /**
     * Finds a file by its stored filename (UUID-prefixed).
     * 
     * @param fileName the stored filename to search for
     * @return Optional containing the FileEntity if found
     */
    Optional<FileEntity> findByFileName(String fileName);
    
    /**
     * Finds all files belonging to a specific user.
     * 
     * @param owner the user whose files to retrieve
     * @return List of FileEntity objects owned by the user
     */
    List<FileEntity> findByOwner(User owner);
    
    /**
     * Finds a file by ID that belongs to a specific user.
     * 
     * <p>This method ensures that users can only access their own files
     * by combining ID lookup with ownership validation.
     * 
     * @param id the file ID to search for
     * @param owner the user who should own the file
     * @return Optional containing the FileEntity if found and owned by user
     */
    Optional<FileEntity> findByIdAndOwner(Long id, User owner);
    
    /**
     * Finds a file by stored filename that belongs to a specific user.
     * 
     * <p>This method ensures that users can only access their own files
     * by combining filename lookup with ownership validation.
     * 
     * @param fileName the stored filename to search for
     * @param owner the user who should own the file
     * @return Optional containing the FileEntity if found and owned by user
     */
    Optional<FileEntity> findByFileNameAndOwner(String fileName, User owner);
}