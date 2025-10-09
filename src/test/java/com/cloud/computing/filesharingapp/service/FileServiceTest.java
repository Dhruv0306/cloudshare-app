package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.FileResponse;
import com.cloud.computing.filesharingapp.dto.FileSharingStats;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Mock
    private FileSharingService fileSharingService;

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
                "Hello World".getBytes());

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
        FileEntity savedEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 11L,
                tempDir.resolve("uuid_test.txt").toString(), testUser);
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
                "Malicious content".getBytes());

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

        FileEntity fileEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, filePath.toString(),
                testUser);
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

    // Tests for sharing functionality

    @Test
    void testGetUserFilesWithSharingInfo() {
        // Given
        FileEntity file1 = createTestFileEntity(1L, "file1.txt");
        FileEntity file2 = createTestFileEntity(2L, "file2.txt");
        List<FileEntity> files = Arrays.asList(file1, file2);

        FileSharingStats stats1 = createTestSharingStats(1L, true, 2, 5);
        FileSharingStats stats2 = createTestSharingStats(2L, false, 0, 0);

        when(fileRepository.findByOwner(testUser)).thenReturn(files);
        when(fileSharingService.getFileSharingStats(1L, testUser)).thenReturn(stats1);
        when(fileSharingService.getFileSharingStats(2L, testUser)).thenReturn(stats2);

        // When
        List<FileResponse> result = fileService.getUserFilesWithSharingInfo(testUser);

        // Then
        assertEquals(2, result.size());

        FileResponse response1 = result.get(0);
        assertEquals(1L, response1.getId());
        assertEquals("file1.txt", response1.getOriginalFileName());
        assertTrue(response1.isShared());
        assertEquals(2, response1.getShareCount());
        assertEquals(5, response1.getTotalAccessCount());
        assertTrue(response1.isHasActiveShares());
        assertTrue(response1.isCanShare());
        assertTrue(response1.isCanDelete());
        assertTrue(response1.isCanDownload());

        FileResponse response2 = result.get(1);
        assertEquals(2L, response2.getId());
        assertEquals("file2.txt", response2.getOriginalFileName());
        assertFalse(response2.isShared());
        assertEquals(0, response2.getShareCount());
        assertEquals(0, response2.getTotalAccessCount());
        assertFalse(response2.isHasActiveShares());

        verify(fileRepository).findByOwner(testUser);
        verify(fileSharingService).getFileSharingStats(1L, testUser);
        verify(fileSharingService).getFileSharingStats(2L, testUser);
    }

    @Test
    void testGetUserFilesWithSharingInfoWhenSharingServiceFails() {
        // Given
        FileEntity file = createTestFileEntity(1L, "test.txt");
        List<FileEntity> files = Arrays.asList(file);

        when(fileRepository.findByOwner(testUser)).thenReturn(files);
        when(fileSharingService.getFileSharingStats(1L, testUser))
                .thenThrow(new RuntimeException("Sharing service error"));

        // When
        List<FileResponse> result = fileService.getUserFilesWithSharingInfo(testUser);

        // Then
        assertEquals(1, result.size());
        FileResponse response = result.get(0);
        assertEquals(1L, response.getId());
        assertEquals("test.txt", response.getOriginalFileName());
        // Should have default values when sharing service fails
        assertFalse(response.isShared());
        assertEquals(0, response.getShareCount());
        assertEquals(0, response.getTotalAccessCount());
        assertFalse(response.isHasActiveShares());
        // Capabilities should still be true for owner
        assertTrue(response.isCanShare());
        assertTrue(response.isCanDelete());
        assertTrue(response.isCanDownload());

        verify(fileRepository).findByOwner(testUser);
        verify(fileSharingService).getFileSharingStats(1L, testUser);
    }

    @Test
    void testGetUserFileByIdWithSharingInfo() {
        // Given
        Long fileId = 1L;
        FileEntity file = createTestFileEntity(fileId, "test.txt");
        FileSharingStats stats = createTestSharingStats(fileId, true, 3, 10);

        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(file));
        when(fileSharingService.getFileSharingStats(fileId, testUser)).thenReturn(stats);

        // When
        Optional<FileResponse> result = fileService.getUserFileByIdWithSharingInfo(fileId, testUser);

        // Then
        assertTrue(result.isPresent());
        FileResponse response = result.get();
        assertEquals(fileId, response.getId());
        assertEquals("test.txt", response.getOriginalFileName());
        assertTrue(response.isShared());
        assertEquals(3, response.getShareCount());
        assertEquals(10, response.getTotalAccessCount());
        assertTrue(response.isHasActiveShares());

        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileSharingService).getFileSharingStats(fileId, testUser);
    }

    @Test
    void testGetUserFileByIdWithSharingInfoNotFound() {
        // Given
        Long fileId = 1L;
        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.empty());

        // When
        Optional<FileResponse> result = fileService.getUserFileByIdWithSharingInfo(fileId, testUser);

        // Then
        assertFalse(result.isPresent());
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileSharingService, never()).getFileSharingStats(any(), any());
    }

    @Test
    void testDeleteUserFileWithShares() throws IOException {
        // Given
        fileService.init();
        Long fileId = 1L;
        String fileName = "test.txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, "Hello World".getBytes());

        FileEntity fileEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, filePath.toString(),
                testUser);
        fileEntity.setId(fileId);

        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(fileEntity));
        when(fileSharingService.revokeAllSharesForFile(fileId, testUser)).thenReturn(3);

        // When
        fileService.deleteUserFile(fileId, testUser);

        // Then
        assertFalse(Files.exists(filePath));
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileSharingService).revokeAllSharesForFile(fileId, testUser);
        verify(fileRepository).deleteById(fileId);
    }

    @Test
    void testDeleteUserFileWithNoShares() throws IOException {
        // Given
        fileService.init();
        Long fileId = 1L;
        String fileName = "test.txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, "Hello World".getBytes());

        FileEntity fileEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, filePath.toString(),
                testUser);
        fileEntity.setId(fileId);

        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(fileEntity));
        when(fileSharingService.revokeAllSharesForFile(fileId, testUser)).thenReturn(0);

        // When
        fileService.deleteUserFile(fileId, testUser);

        // Then
        assertFalse(Files.exists(filePath));
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileSharingService).revokeAllSharesForFile(fileId, testUser);
        verify(fileRepository).deleteById(fileId);
    }

    @Test
    void testDeleteUserFilePhysicalFileNotFound() throws IOException {
        // Given
        fileService.init();
        Long fileId = 1L;
        String fileName = "nonexistent.txt";
        Path filePath = tempDir.resolve(fileName);
        // Note: not creating the physical file

        FileEntity fileEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, filePath.toString(),
                testUser);
        fileEntity.setId(fileId);

        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(fileEntity));
        when(fileSharingService.revokeAllSharesForFile(fileId, testUser)).thenReturn(0);

        // When
        fileService.deleteUserFile(fileId, testUser);

        // Then - should still delete database record even if physical file doesn't
        // exist
        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileSharingService).revokeAllSharesForFile(fileId, testUser);
        verify(fileRepository).deleteById(fileId);
    }

    /**
     * Test enhanced file response creation with sharing statistics
     */
    @Test
    void testFileResponseCreationWithSharingStats() {
        // Given
        FileEntity file = createTestFileEntity(1L, "test.txt");
        FileSharingStats stats = createTestSharingStats(1L, true, 2, 10);
        stats.setLastSharedAt(LocalDateTime.now().minusHours(2));
        stats.setLastAccessedAt(LocalDateTime.now().minusMinutes(30));

        when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(file));
        when(fileSharingService.getFileSharingStats(1L, testUser)).thenReturn(stats);

        // When
        Optional<FileResponse> responseOpt = fileService.getUserFileByIdWithSharingInfo(1L, testUser);

        // Then
        assertTrue(responseOpt.isPresent());
        FileResponse response = responseOpt.get();
        assertEquals(1L, response.getId());
        assertEquals("test.txt", response.getOriginalFileName());
        assertTrue(response.isShared());
        assertEquals(2, response.getShareCount());
        assertEquals(10, response.getTotalAccessCount());
        assertTrue(response.isHasActiveShares());
        assertNotNull(response.getLastSharedAt());
        assertNotNull(response.getLastAccessedAt());

        // Verify capabilities are set correctly for owner
        assertTrue(response.isCanShare());
        assertTrue(response.isCanDelete());
        assertTrue(response.isCanDownload());
    }

    /**
     * Test file response creation with null sharing stats
     */
    @Test
    void testFileResponseCreationWithNullSharingStats() {
        // Given
        FileEntity file = createTestFileEntity(1L, "test.txt");
        when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(file));
        when(fileSharingService.getFileSharingStats(1L, testUser)).thenReturn(null);

        // When
        Optional<FileResponse> responseOpt = fileService.getUserFileByIdWithSharingInfo(1L, testUser);

        // Then
        assertTrue(responseOpt.isPresent());
        FileResponse response = responseOpt.get();
        assertEquals(1L, response.getId());
        assertEquals("test.txt", response.getOriginalFileName());
        assertFalse(response.isShared());
        assertEquals(0, response.getShareCount());
        assertEquals(0, response.getTotalAccessCount());
        assertFalse(response.isHasActiveShares());

        // Capabilities should still be true for owner
        assertTrue(response.isCanShare());
        assertTrue(response.isCanDelete());
        assertTrue(response.isCanDownload());
    }

    /**
     * Test bulk file operations with sharing info
     */
    @Test
    void testGetUserFilesWithSharingInfoLargeDataset() {
        // Given - Create a larger dataset to test performance
        List<FileEntity> files = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            files.add(createTestFileEntity((long) i, "file" + i + ".txt"));
        }

        when(fileRepository.findByOwner(testUser)).thenReturn(files);

        // Mock sharing stats for some files
        for (int i = 1; i <= 10; i++) {
            boolean isShared = i % 2 == 0; // Even numbered files are shared
            int shareCount = isShared ? i / 2 : 0;
            FileSharingStats stats = createTestSharingStats((long) i, isShared, shareCount, shareCount * 3);
            when(fileSharingService.getFileSharingStats((long) i, testUser)).thenReturn(stats);
        }

        // When
        List<FileResponse> result = fileService.getUserFilesWithSharingInfo(testUser);

        // Then
        assertEquals(10, result.size());

        // Verify shared files have correct stats
        for (int i = 0; i < result.size(); i++) {
            FileResponse response = result.get(i);
            long fileId = response.getId();
            boolean shouldBeShared = fileId % 2 == 0;

            assertEquals(shouldBeShared, response.isShared());
            if (shouldBeShared) {
                assertTrue(response.getShareCount() > 0);
                assertTrue(response.getTotalAccessCount() > 0);
            } else {
                assertEquals(0, response.getShareCount());
                assertEquals(0, response.getTotalAccessCount());
            }
        }

        verify(fileRepository).findByOwner(testUser);
        // Verify sharing service was called for each file
        for (int i = 1; i <= 10; i++) {
            verify(fileSharingService).getFileSharingStats((long) i, testUser);
        }
    }

    /**
     * Test error handling when sharing service throws exception for some files
     */
    @Test
    void testGetUserFilesWithSharingInfoPartialFailure() {
        // Given
        FileEntity file1 = createTestFileEntity(1L, "file1.txt");
        FileEntity file2 = createTestFileEntity(2L, "file2.txt");
        FileEntity file3 = createTestFileEntity(3L, "file3.txt");
        List<FileEntity> files = Arrays.asList(file1, file2, file3);

        when(fileRepository.findByOwner(testUser)).thenReturn(files);

        // Mock sharing service to succeed for some files and fail for others
        when(fileSharingService.getFileSharingStats(1L, testUser))
                .thenReturn(createTestSharingStats(1L, true, 2, 5));
        when(fileSharingService.getFileSharingStats(2L, testUser))
                .thenThrow(new RuntimeException("Service unavailable"));
        when(fileSharingService.getFileSharingStats(3L, testUser))
                .thenReturn(createTestSharingStats(3L, false, 0, 0));

        // When
        List<FileResponse> result = fileService.getUserFilesWithSharingInfo(testUser);

        // Then
        assertEquals(3, result.size());

        // File 1 should have sharing info
        FileResponse response1 = result.get(0);
        assertTrue(response1.isShared());
        assertEquals(2, response1.getShareCount());

        // File 2 should have default values due to service failure
        FileResponse response2 = result.get(1);
        assertFalse(response2.isShared());
        assertEquals(0, response2.getShareCount());

        // File 3 should have correct no-sharing info
        FileResponse response3 = result.get(2);
        assertFalse(response3.isShared());
        assertEquals(0, response3.getShareCount());

        verify(fileRepository).findByOwner(testUser);
        verify(fileSharingService).getFileSharingStats(1L, testUser);
        verify(fileSharingService).getFileSharingStats(2L, testUser);
        verify(fileSharingService).getFileSharingStats(3L, testUser);
    }

    /**
     * Test file deletion with sharing service failure
     */
    @Test
    void testDeleteUserFileWithSharingServiceFailure() throws IOException {
        // Given
        fileService.init();
        Long fileId = 1L;
        String fileName = "test.txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, "Hello World".getBytes());

        FileEntity fileEntity = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, filePath.toString(),
                testUser);
        fileEntity.setId(fileId);

        when(fileRepository.findByIdAndOwner(fileId, testUser)).thenReturn(Optional.of(fileEntity));
        when(fileSharingService.revokeAllSharesForFile(fileId, testUser))
                .thenThrow(new RuntimeException("Sharing service error"));

        // When & Then - Should still proceed with file deletion even if sharing service
        // fails
        assertDoesNotThrow(() -> fileService.deleteUserFile(fileId, testUser));

        // Physical file should be deleted
        assertFalse(Files.exists(filePath));

        verify(fileRepository).findByIdAndOwner(fileId, testUser);
        verify(fileSharingService).revokeAllSharesForFile(fileId, testUser);
        verify(fileRepository).deleteById(fileId);
    }

    // Helper methods for creating test objects

    private FileEntity createTestFileEntity(Long id, String originalFileName) {
        FileEntity entity = new FileEntity(
                "uuid_" + originalFileName,
                originalFileName,
                "text/plain",
                1024L,
                "/path/to/" + originalFileName,
                testUser);
        entity.setId(id);
        entity.setUploadTime(LocalDateTime.now());
        return entity;
    }

    private FileSharingStats createTestSharingStats(Long fileId, boolean isShared, int activeShares,
            int totalAccessCount) {
        FileSharingStats stats = new FileSharingStats();
        stats.setFileId(fileId);
        stats.setShared(isShared);
        stats.setActiveShares(activeShares);
        stats.setTotalAccessCount(totalAccessCount);
        stats.setHasActiveShares(activeShares > 0);

        if (isShared) {
            stats.setLastSharedAt(LocalDateTime.now().minusHours(1));
        }
        if (totalAccessCount > 0) {
            stats.setLastAccessedAt(LocalDateTime.now().minusMinutes(30));
        }

        return stats;
    }
}