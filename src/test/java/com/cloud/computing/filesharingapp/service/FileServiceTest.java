package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileService fileService;

    @TempDir
    Path tempDir;

    private User testUser;
    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setId(1L);
        
        mockFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "Hello World".getBytes()
        );

        // Set the upload directory to temp directory
        ReflectionTestUtils.setField(fileService, "uploadDir", tempDir.toString());
    }

    @Test
    void testInit() {
        // When
        fileService.init();

        // Then
        assertTrue(Files.exists(tempDir));
        assertTrue(Files.isDirectory(tempDir));
    }

    @Test
    void testStoreFile() throws IOException {
        // Given
        fileService.init();
        FileEntity savedEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 11L, tempDir.resolve("uuid_test.txt").toString(), testUser);
        savedEntity.setId(1L);
        
        when(fileRepository.save(any(FileEntity.class))).thenReturn(savedEntity);

        // When
        FileEntity result = fileService.storeFile(mockFile, testUser);

        // Then
        assertNotNull(result);
        assertEquals("test.txt", result.getOriginalFileName());
        assertEquals("text/plain", result.getContentType());
        assertEquals(11L, result.getFileSize());
        assertEquals(testUser, result.getOwner());
        
        verify(fileRepository).save(any(FileEntity.class));
    }

    @Test
    void testStoreFileWithInvalidPath() {
        // Given
        fileService.init();
        MockMultipartFile maliciousFile = new MockMultipartFile(
            "file",
            "../../../malicious.txt",
            "text/plain",
            "Malicious content".getBytes()
        );

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileService.storeFile(maliciousFile, testUser);
        });
        
        assertTrue(exception.getMessage().contains("Invalid file name"));
    }

    @Test
    void testGetUserFiles() {
        // Given
        FileEntity file1 = new FileEntity("uuid1_file1.txt", "file1.txt", "text/plain", 1024L, "/path1", testUser);
        FileEntity file2 = new FileEntity("uuid2_file2.txt", "file2.txt", "text/plain", 2048L, "/path2", testUser);
        List<FileEntity> expectedFiles = Arrays.asList(file1, file2);
        
        when(fileRepository.findByOwner(testUser)).thenReturn(expectedFiles);

        // When
        List<FileEntity> result = fileService.getUserFiles(testUser);

        // Then
        assertEquals(2, result.size());
        assertEquals(expectedFiles, result);
        verify(fileRepository).findByOwner(testUser);
    }

    @Test
    void testGetUserFileById() {
        // Given
        Long fileId = 1L;
        FileEntity expectedFile = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path", testUser);
        
        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(expectedFile));

        // When
        Optional<FileEntity> result = fileService.getUserFileById(fileId, testUser);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedFile, result.get());
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
    }

    @Test
    void testGetUserFileByFileName() {
        // Given
        String fileName = "uuid_test.txt";
        FileEntity expectedFile = new FileEntity(fileName, "test.txt", "text/plain", 1024L, "/path", testUser);
        
        when(fileRepository.findByFileNameAndOwner(fileName, testUser)).thenReturn(Optional.of(expectedFile));

        // When
        Optional<FileEntity> result = fileService.getUserFileByFileName(fileName, testUser);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedFile, result.get());
        verify(fileRepository).findByFileNameAndOwner(fileName, testUser);
    }

    @Test
    void testLoadFileAsResource() throws IOException {
        // Given
        fileService.init();
        String fileName = "test.txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, "Hello World".getBytes());

        // When
        Resource resource = fileService.loadFileAsResource(fileName);

        // Then
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void testLoadFileAsResourceNotFound() {
        // Given
        fileService.init();
        String fileName = "nonexistent.txt";

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileService.loadFileAsResource(fileName);
        });
        
        assertTrue(exception.getMessage().contains("File not found"));
    }

    @Test
    void testDeleteUserFile() throws IOException {
        // Given
        fileService.init();
        Long fileId = 1L;
        String fileName = "test.txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, "Hello World".getBytes());
        
        FileEntity fileEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, filePath.toString(), testUser);
        fileEntity.setId(fileId);
        
        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(fileEntity));

        // When
        fileService.deleteUserFile(fileId, testUser);

        // Then
        assertFalse(Files.exists(filePath));
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileRepository).deleteById(fileId);
    }

    @Test
    void testDeleteUserFileNotFound() {
        // Given
        Long fileId = 1L;
        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileService.deleteUserFile(fileId, testUser);
        });
        
        assertTrue(exception.getMessage().contains("File not found or access denied"));
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileRepository, never()).deleteById(any());
    }
}