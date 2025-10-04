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

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    @Autowired
    private FileRepository fileRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

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

    public List<FileEntity> getAllFiles() {
        return fileRepository.findAll();
    }

    public List<FileEntity> getUserFiles(User user) {
        return fileRepository.findByOwner(user);
    }

    public Optional<FileEntity> getFileById(Long id) {
        return fileRepository.findById(id);
    }

    public Optional<FileEntity> getUserFileById(Long id, User user) {
        return fileRepository.findByIdAndOwner(id, user);
    }

    public Optional<FileEntity> getFileByFileName(String fileName) {
        return fileRepository.findByFileName(fileName);
    }

    public Optional<FileEntity> getUserFileByFileName(String fileName, User user) {
        return fileRepository.findByFileNameAndOwner(fileName, user);
    }

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