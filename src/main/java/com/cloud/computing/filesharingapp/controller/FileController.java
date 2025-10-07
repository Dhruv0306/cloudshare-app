package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
import com.cloud.computing.filesharingapp.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for file management operations.
 * 
 * <p>This controller provides endpoints for authenticated users to:
 * <ul>
 *   <li>Upload files to their personal storage</li>
 *   <li>Download their uploaded files</li>
 *   <li>List all their files</li>
 *   <li>Retrieve specific file metadata</li>
 *   <li>Delete their files</li>
 * </ul>
 * 
 * <p>All operations are user-scoped, ensuring users can only access their own files.
 * The controller includes comprehensive logging for security auditing and debugging.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Uploads a file for the authenticated user.
     * 
     * <p>This endpoint accepts multipart file uploads and stores them securely
     * with user isolation. Each file is assigned a unique identifier to prevent
     * naming conflicts and unauthorized access.
     * 
     * @param file the multipart file to upload
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the created FileEntity or error message
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("File upload request from user: {} (ID: {}), filename: {}, size: {} bytes", 
                   userPrincipal.getUsername(), userPrincipal.getId(), file.getOriginalFilename(), file.getSize());
        
        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            FileEntity fileEntity = fileService.storeFile(file, user);
            
            logger.info("File uploaded successfully - ID: {}, filename: {}, user: {}", 
                       fileEntity.getId(), fileEntity.getOriginalFileName(), user.getUsername());
            
            return ResponseEntity.ok().body(fileEntity);
        } catch (Exception ex) {
            logger.error("File upload failed for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("Could not upload file: " + ex.getMessage());
        }
    }
    
    /**
     * Downloads a file by its stored filename for the authenticated user.
     * 
     * <p>This endpoint retrieves files that belong to the authenticated user only.
     * The response includes appropriate headers for file download with the original
     * filename and content type.
     * 
     * @param fileName the stored filename (UUID-prefixed) of the file to download
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the file resource or 404 if not found/unauthorized
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("File download request from user: {} (ID: {}), filename: {}", 
                   userPrincipal.getUsername(), userPrincipal.getId(), fileName);
        
        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            Optional<FileEntity> fileEntity = fileService.getUserFileByFileName(fileName, user);
            if (fileEntity.isEmpty()) {
                logger.warn("File not found or access denied - filename: {}, user: {}", fileName, userPrincipal.getUsername());
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = fileService.loadFileAsResource(fileName);
            String contentType = fileEntity.get().getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            logger.info("File downloaded successfully - filename: {}, original: {}, user: {}", 
                       fileName, fileEntity.get().getOriginalFileName(), userPrincipal.getUsername());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + fileEntity.get().getOriginalFileName() + "\"")
                    .body(resource);
        } catch (Exception ex) {
            logger.error("File download failed for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Retrieves all files belonging to the authenticated user.
     * 
     * <p>Returns a list of FileEntity objects containing metadata for all files
     * uploaded by the current user. This includes file names, sizes, upload dates,
     * and other relevant information.
     * 
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing a list of the user's files
     */
    @GetMapping
    public ResponseEntity<List<FileEntity>> getUserFiles(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.debug("File list request from user: {} (ID: {})", userPrincipal.getUsername(), userPrincipal.getId());
        
        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<FileEntity> files = fileService.getUserFiles(user);
            
            logger.info("Retrieved {} files for user: {}", files.size(), userPrincipal.getUsername());
            
            return ResponseEntity.ok(files);
        } catch (Exception ex) {
            logger.error("Failed to retrieve files for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Retrieves a specific file by its ID for the authenticated user.
     * 
     * <p>Returns the FileEntity metadata for a file with the specified ID,
     * but only if the file belongs to the authenticated user.
     * 
     * @param id the unique identifier of the file
     * @param authentication the current user's authentication context
     * @return ResponseEntity containing the FileEntity or 404 if not found/unauthorized
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileEntity> getUserFileById(@PathVariable Long id, Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            Optional<FileEntity> file = fileService.getUserFileById(id, user);
            return file.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Deletes a file by its ID for the authenticated user.
     * 
     * <p>Permanently removes both the file record from the database and the
     * physical file from storage. Only files belonging to the authenticated
     * user can be deleted.
     * 
     * @param id the unique identifier of the file to delete
     * @param authentication the current user's authentication context
     * @return ResponseEntity with success message or error details
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUserFile(@PathVariable Long id, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        logger.info("File deletion request from user: {} (ID: {}), file ID: {}", 
                   userPrincipal.getUsername(), userPrincipal.getId(), id);
        
        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            fileService.deleteUserFile(id, user);
            
            logger.info("File deleted successfully - ID: {}, user: {}", id, userPrincipal.getUsername());
            
            return ResponseEntity.ok().body("File deleted successfully");
        } catch (Exception ex) {
            logger.error("File deletion failed for user: {} - {}", userPrincipal.getUsername(), ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("Could not delete file: " + ex.getMessage());
        }
    }
}