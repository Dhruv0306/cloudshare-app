package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

class FileEntityTest {

    private FileEntity fileEntity;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User("testuser", "test@example.com", "password123");
        fileEntity = new FileEntity();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(fileEntity);
    }

    @Test
    void testParameterizedConstructor() {
        String fileName = "test_file.txt";
        String originalFileName = "file.txt";
        String contentType = "text/plain";
        Long fileSize = 1024L;
        String filePath = "/uploads/test_file.txt";

        FileEntity file = new FileEntity(fileName, originalFileName, contentType, fileSize, filePath, owner);

        assertEquals(fileName, file.getFileName());
        assertEquals(originalFileName, file.getOriginalFileName());
        assertEquals(contentType, file.getContentType());
        assertEquals(fileSize, file.getFileSize());
        assertEquals(filePath, file.getFilePath());
        assertEquals(owner, file.getOwner());
        assertNotNull(file.getUploadTime());
    }

    @Test
    void testSettersAndGetters() {
        Long id = 1L;
        String fileName = "test_file.txt";
        String originalFileName = "file.txt";
        String contentType = "text/plain";
        Long fileSize = 1024L;
        String filePath = "/uploads/test_file.txt";
        LocalDateTime uploadTime = LocalDateTime.now();

        fileEntity.setId(id);
        fileEntity.setFileName(fileName);
        fileEntity.setOriginalFileName(originalFileName);
        fileEntity.setContentType(contentType);
        fileEntity.setFileSize(fileSize);
        fileEntity.setFilePath(filePath);
        fileEntity.setOwner(owner);
        fileEntity.setUploadTime(uploadTime);

        assertEquals(id, fileEntity.getId());
        assertEquals(fileName, fileEntity.getFileName());
        assertEquals(originalFileName, fileEntity.getOriginalFileName());
        assertEquals(contentType, fileEntity.getContentType());
        assertEquals(fileSize, fileEntity.getFileSize());
        assertEquals(filePath, fileEntity.getFilePath());
        assertEquals(owner, fileEntity.getOwner());
        assertEquals(uploadTime, fileEntity.getUploadTime());
    }

    @Test
    void testOwnerRelationship() {
        fileEntity.setOwner(owner);
        assertEquals(owner, fileEntity.getOwner());
        assertEquals("testuser", fileEntity.getOwner().getUsername());
    }
}