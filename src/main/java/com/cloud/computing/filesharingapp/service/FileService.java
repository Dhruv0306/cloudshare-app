package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for managing file operations including upload, download, and deletion.
 * 
 * <p>This service provides secure file management capabilities with the following features:
 * <ul>
 *   <li>Secure file storage with UUID-based naming to prevent conflicts</li>
 *   <li>User-scoped file access ensuring data isolation</li>
 *   <li>Path traversal attack prevention</li>
 *   <li>Comprehensive logging for security auditing</li>
 *   <li>Automatic directory creation and management</li>
 * </ul>
 * 
 * <p>Files are stored in a configurable upload directory with unique names generated
 * using UUIDs to prevent naming conflicts and unauthorized access attempts.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    @Autowired
    private FileRepository fileRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

    /**
     * Initializes the file storage system by creating necessary directories.
     * 
     * <p>This method sets up the file storage location based on the configured
     * upload directory path. If the directory doesn't exist, it will be created
     * with appropriate permissions.
     * 
     * @throws RuntimeException if the storage directory cannot be created
     */
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        logger.info("Initializing file storage location: {}", this.fileStorageLocation);
        
        try {
            Files.createDirectories(this.fileStorageLocation);
            logger.info("File storage directory created/verified successfully");
        } catch (Exception ex) {
            logger.error("Could not create file storage directory: {}", this.fileStorageLocation, ex);
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /**
     * Stores an uploaded file securely with user ownership tracking.
     * 
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Generates a unique filename using UUID to prevent conflicts</li>
     *   <li>Validates the filename to prevent path traversal attacks</li>
     *   <li>Copies the file to the secure storage location</li>
     *   <li>Creates a database record with file metadata and ownership</li>
     * </ul>
     * 
     * @param file the multipart file to store
     * @param owner the user who owns this file
     * @return FileEntity representing the stored file with metadata
     * @throws RuntimeException if file storage fails or filename is invalid
     */
    public FileEntity storeFile(MultipartFile file, User owner) {
        if (fileStorageLocation == null) {
            init();
        }

        String rawFileName = file.getOriginalFilename();
        String originalFileName = StringUtils.cleanPath(rawFileName != null ? rawFileName : "unknown");
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;

        logger.info("Storing file: {} (original: {}) for user: {} (ID: {}), size: {} bytes", 
                   fileName, originalFileName, owner.getUsername(), owner.getId(), file.getSize());

        try {
            if (fileName.contains("..")) {
                logger.warn("Invalid file name detected (path traversal attempt): {}", fileName);
                throw new RuntimeException("Invalid file name: " + fileName);
            }

            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            FileEntity fileEntity = new FileEntity(
                    fileName,
                    originalFileName,
                    file.getContentType(),
                    file.getSize(),
                    targetLocation.toString(),
                    owner);

            FileEntity savedEntity = fileRepository.save(fileEntity);
            
            logger.info("File stored successfully - ID: {}, path: {}", savedEntity.getId(), targetLocation);
            
            return savedEntity;

        } catch (IOException ex) {
            logger.error("Failed to store file: {} - {}", fileName, ex.getMessage(), ex);
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    /**
     * Loads a file as a Spring Resource for download operations.
     * 
     * <p>This method resolves the file path and creates a Resource object
     * that can be used in HTTP responses for file downloads. The method
     * ensures the file exists before returning the resource.
     * 
     * @param fileName the stored filename (UUID-prefixed) to load
     * @return Resource representing the file for download
     * @throws RuntimeException if the file is not found or cannot be accessed
     */
    public Resource loadFileAsResource(String fileName) {
        try {
            if (fileStorageLocation == null) {
                init();
            }

            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found " + fileName, ex);
        }
    }

    /**
     * Retrieves all files in the system (admin function).
     * 
     * @return List of all FileEntity objects in the database
     */
    public List<FileEntity> getAllFiles() {
        return fileRepository.findAll();
    }

    /**
     * Retrieves all files belonging to a specific user.
     * 
     * @param user the user whose files to retrieve
     * @return List of FileEntity objects owned by the user
     */
    public List<FileEntity> getUserFiles(User user) {
        return fileRepository.findByOwner(user);
    }

    /**
     * Retrieves a file by its ID (admin function).
     * 
     * @param id the unique identifier of the file
     * @return Optional containing the FileEntity if found
     */
    public Optional<FileEntity> getFileById(Long id) {
        return fileRepository.findById(id);
    }

    /**
     * Retrieves a file by its ID for a specific user (user-scoped access).
     * 
     * @param id the unique identifier of the file
     * @param user the user who should own the file
     * @return Optional containing the FileEntity if found and owned by user
     */
    public Optional<FileEntity> getUserFileById(Long id, User user) {
        return fileRepository.findByIdAndOwner(id, user);
    }

    /**
     * Retrieves a file by its stored filename (admin function).
     * 
     * @param fileName the stored filename to search for
     * @return Optional containing the FileEntity if found
     */
    public Optional<FileEntity> getFileByFileName(String fileName) {
        return fileRepository.findByFileName(fileName);
    }

    /**
     * Retrieves a file by its stored filename for a specific user (user-scoped access).
     * 
     * @param fileName the stored filename to search for
     * @param user the user who should own the file
     * @return Optional containing the FileEntity if found and owned by user
     */
    public Optional<FileEntity> getUserFileByFileName(String fileName, User user) {
        return fileRepository.findByFileNameAndOwner(fileName, user);
    }

    /**
     * Deletes a file by its ID (admin function).
     * 
     * <p>This method removes both the physical file from storage and the
     * database record. It should be used carefully as it bypasses user
     * ownership checks.
     * 
     * @param id the unique identifier of the file to delete
     * @throws RuntimeException if file deletion fails
     */
    public void deleteFile(Long id) {
        Optional<FileEntity> fileEntity = fileRepository.findById(id);
        if (fileEntity.isPresent()) {
            try {
                Path filePath = Paths.get(fileEntity.get().getFilePath());
                Files.deleteIfExists(filePath);
                fileRepository.deleteById(id);
            } catch (IOException ex) {
                throw new RuntimeException("Could not delete file", ex);
            }
        }
    }

    /**
     * Deletes a file by its ID for a specific user (user-scoped deletion).
     * 
     * <p>This method ensures that only the file owner can delete their files.
     * It performs the following operations:
     * <ul>
     *   <li>Verifies the user owns the file</li>
     *   <li>Removes the physical file from storage</li>
     *   <li>Removes the database record</li>
     *   <li>Logs all operations for security auditing</li>
     * </ul>
     * 
     * @param id the unique identifier of the file to delete
     * @param user the user requesting the deletion (must be the owner)
     * @throws RuntimeException if file is not found, access is denied, or deletion fails
     */
    public void deleteUserFile(Long id, User user) {
        logger.info("Deleting file ID: {} for user: {} (ID: {})", id, user.getUsername(), user.getId());
        
        Optional<FileEntity> fileEntity = fileRepository.findByIdAndOwner(id, user);
        if (fileEntity.isPresent()) {
            FileEntity entity = fileEntity.get();
            try {
                Path filePath = Paths.get(entity.getFilePath());
                boolean deleted = Files.deleteIfExists(filePath);
                
                if (deleted) {
                    logger.info("Physical file deleted: {}", filePath);
                } else {
                    logger.warn("Physical file not found (may have been already deleted): {}", filePath);
                }
                
                fileRepository.deleteById(id);
                logger.info("File record deleted from database - ID: {}, original name: {}", 
                           id, entity.getOriginalFileName());
                
            } catch (IOException ex) {
                logger.error("Failed to delete physical file: {} - {}", entity.getFilePath(), ex.getMessage(), ex);
                throw new RuntimeException("Could not delete file", ex);
            }
        } else {
            logger.warn("File not found or access denied - ID: {}, user: {}", id, user.getUsername());
            throw new RuntimeException("File not found or access denied");
        }
    }
}