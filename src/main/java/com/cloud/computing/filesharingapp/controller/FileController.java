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

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserRepository userRepository;
    
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