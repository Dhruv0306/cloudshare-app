package com.cloud.computing.filesharingapp.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a file in the file sharing application.
 * 
 * <p>This entity stores metadata about uploaded files including:
 * <ul>
 *   <li>File identification (unique filename and original name)</li>
 *   <li>File properties (content type, size, storage path)</li>
 *   <li>Upload timestamp for tracking</li>
 *   <li>Owner relationship for access control</li>
 * </ul>
 * 
 * <p>Files are stored with UUID-prefixed names to prevent conflicts and
 * unauthorized access while preserving the original filename for user display.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "files")
public class FileEntity {
    /** Unique identifier for the file record */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /** Stored filename (UUID-prefixed) used for file system storage */
    @Column(nullable = false)
    private String fileName;
    
    /** Original filename as uploaded by the user */
    @Column(nullable = false)
    private String originalFileName;
    
    /** MIME content type of the file */
    @Column(nullable = false)
    private String contentType;
    
    /** Size of the file in bytes */
    @Column(nullable = false)
    private Long fileSize;
    
    /** Full file system path where the file is stored */
    @Column(nullable = false)
    private String filePath;
    
    /** Timestamp when the file was uploaded */
    @Column(nullable = false)
    private LocalDateTime uploadTime;
    
    /** User who owns this file (many-to-one relationship) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonBackReference
    private User owner;
    
    /**
     * Default constructor for JPA.
     */
    public FileEntity() {}
    
    /**
     * Constructor for creating a new file entity with all required properties.
     * 
     * @param fileName the stored filename (UUID-prefixed)
     * @param originalFileName the original filename from upload
     * @param contentType the MIME content type
     * @param fileSize the file size in bytes
     * @param filePath the full storage path
     * @param owner the user who owns this file
     */
    public FileEntity(String fileName, String originalFileName, String contentType, Long fileSize, String filePath, User owner) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.filePath = filePath;
        this.owner = owner;
        this.uploadTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
}