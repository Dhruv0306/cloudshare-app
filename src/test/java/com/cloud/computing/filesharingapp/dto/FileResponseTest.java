package com.cloud.computing.filesharingapp.dto;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FileResponse DTO.
 * 
 * <p>Tests the FileResponse data transfer object including:
 * <ul>
 *   <li>Construction from FileEntity</li>
 *   <li>Default values for sharing-related fields</li>
 *   <li>Getter and setter functionality</li>
 *   <li>File capabilities and permissions</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class FileResponseTest {

    private User testUser;
    private FileEntity testFileEntity;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setId(1L);
        
        testTime = LocalDateTime.now();
        
        testFileEntity = new FileEntity(
            "uuid_test.txt",
            "test.txt",
            "text/plain",
            1024L,
            "/path/to/file",
            testUser
        );
        testFileEntity.setId(1L);
        testFileEntity.setUploadTime(testTime);
    }

    /**
     * Test default constructor creates empty FileResponse
     */
    @Test
    void testDefaultConstructor() {
        // When
        FileResponse response = new FileResponse();

        // Then
        assertNull(response.getId());
        assertNull(response.getFileName());
        assertNull(response.getOriginalFileName());
        assertNull(response.getContentType());
        assertNull(response.getFileSize());
        assertNull(response.getUploadTime());
        
        // Sharing-related fields should have default values
        assertFalse(response.isShared());
        assertEquals(0, response.getShareCount());
        assertEquals(0, response.getTotalAccessCount());
        assertNull(response.getLastSharedAt());
        assertNull(response.getLastAccessedAt());
        assertFalse(response.isHasActiveShares());
        
        // Capabilities should be false by default
        assertFalse(response.isCanShare());
        assertFalse(response.isCanDelete());
        assertFalse(response.isCanDownload());
    }

    /**
     * Test constructor with FileEntity parameter
     */
    @Test
    void testConstructorWithFileEntity() {
        // When
        FileResponse response = new FileResponse(testFileEntity);

        // Then
        assertEquals(1L, response.getId());
        assertEquals("uuid_test.txt", response.getFileName());
        assertEquals("test.txt", response.getOriginalFileName());
        assertEquals("text/plain", response.getContentType());
        assertEquals(1024L, response.getFileSize());
        assertEquals(testTime, response.getUploadTime());
        
        // Sharing-related fields should have default values
        assertFalse(response.isShared());
        assertEquals(0, response.getShareCount());
        assertEquals(0, response.getTotalAccessCount());
        assertNull(response.getLastSharedAt());
        assertNull(response.getLastAccessedAt());
        assertFalse(response.isHasActiveShares());
        
        // Capabilities should be true for owner by default
        assertTrue(response.isCanShare());
        assertTrue(response.isCanDelete());
        assertTrue(response.isCanDownload());
    }

    /**
     * Test all getter and setter methods
     */
    @Test
    void testGettersAndSetters() {
        // Given
        FileResponse response = new FileResponse();
        LocalDateTime sharedTime = LocalDateTime.now().minusHours(1);
        LocalDateTime accessedTime = LocalDateTime.now().minusMinutes(30);

        // When
        response.setId(2L);
        response.setFileName("uuid_document.pdf");
        response.setOriginalFileName("document.pdf");
        response.setContentType("application/pdf");
        response.setFileSize(2048L);
        response.setUploadTime(testTime);
        response.setShared(true);
        response.setShareCount(3);
        response.setTotalAccessCount(15);
        response.setLastSharedAt(sharedTime);
        response.setLastAccessedAt(accessedTime);
        response.setHasActiveShares(true);
        response.setCanShare(false);
        response.setCanDelete(false);
        response.setCanDownload(true);

        // Then
        assertEquals(2L, response.getId());
        assertEquals("uuid_document.pdf", response.getFileName());
        assertEquals("document.pdf", response.getOriginalFileName());
        assertEquals("application/pdf", response.getContentType());
        assertEquals(2048L, response.getFileSize());
        assertEquals(testTime, response.getUploadTime());
        assertTrue(response.isShared());
        assertEquals(3, response.getShareCount());
        assertEquals(15, response.getTotalAccessCount());
        assertEquals(sharedTime, response.getLastSharedAt());
        assertEquals(accessedTime, response.getLastAccessedAt());
        assertTrue(response.isHasActiveShares());
        assertFalse(response.isCanShare());
        assertFalse(response.isCanDelete());
        assertTrue(response.isCanDownload());
    }

    /**
     * Test sharing statistics integration
     */
    @Test
    void testSharingStatisticsIntegration() {
        // Given
        FileResponse response = new FileResponse(testFileEntity);
        LocalDateTime sharedTime = LocalDateTime.now().minusHours(2);
        LocalDateTime accessedTime = LocalDateTime.now().minusMinutes(15);

        // When - Simulate setting sharing statistics
        response.setShared(true);
        response.setShareCount(5);
        response.setTotalAccessCount(25);
        response.setLastSharedAt(sharedTime);
        response.setLastAccessedAt(accessedTime);
        response.setHasActiveShares(true);

        // Then
        assertTrue(response.isShared());
        assertEquals(5, response.getShareCount());
        assertEquals(25, response.getTotalAccessCount());
        assertEquals(sharedTime, response.getLastSharedAt());
        assertEquals(accessedTime, response.getLastAccessedAt());
        assertTrue(response.isHasActiveShares());
        
        // Original file data should remain unchanged
        assertEquals("test.txt", response.getOriginalFileName());
        assertEquals(1024L, response.getFileSize());
        assertEquals("text/plain", response.getContentType());
    }

    /**
     * Test file capabilities for different scenarios
     */
    @Test
    void testFileCapabilities() {
        // Given
        FileResponse response = new FileResponse(testFileEntity);

        // When - Test owner capabilities (default)
        // Then
        assertTrue(response.isCanShare());
        assertTrue(response.isCanDelete());
        assertTrue(response.isCanDownload());

        // When - Test restricted capabilities
        response.setCanShare(false);
        response.setCanDelete(false);
        response.setCanDownload(false);

        // Then
        assertFalse(response.isCanShare());
        assertFalse(response.isCanDelete());
        assertFalse(response.isCanDownload());

        // When - Test mixed capabilities
        response.setCanShare(true);
        response.setCanDelete(false);
        response.setCanDownload(true);

        // Then
        assertTrue(response.isCanShare());
        assertFalse(response.isCanDelete());
        assertTrue(response.isCanDownload());
    }

    /**
     * Test with null FileEntity values
     */
    @Test
    void testWithNullFileEntityValues() {
        // Given
        FileEntity fileWithNulls = new FileEntity(
            null,
            null,
            null,
            null,
            null,
            testUser,
            null  // uploadTime
        );

        // When
        FileResponse response = new FileResponse(fileWithNulls);

        // Then
        assertNull(response.getFileName());
        assertNull(response.getOriginalFileName());
        assertNull(response.getContentType());
        assertNull(response.getFileSize());
        assertNull(response.getUploadTime());
        
        // Sharing and capability defaults should still be set
        assertFalse(response.isShared());
        assertEquals(0, response.getShareCount());
        assertTrue(response.isCanShare());
        assertTrue(response.isCanDelete());
        assertTrue(response.isCanDownload());
    }

    /**
     * Test edge cases for sharing statistics
     */
    @Test
    void testSharingStatisticsEdgeCases() {
        // Given
        FileResponse response = new FileResponse();

        // When - Test with zero values
        response.setShareCount(0);
        response.setTotalAccessCount(0);
        response.setShared(false);
        response.setHasActiveShares(false);

        // Then
        assertEquals(0, response.getShareCount());
        assertEquals(0, response.getTotalAccessCount());
        assertFalse(response.isShared());
        assertFalse(response.isHasActiveShares());

        // When - Test with large values
        response.setShareCount(Integer.MAX_VALUE);
        response.setTotalAccessCount(Integer.MAX_VALUE);

        // Then
        assertEquals(Integer.MAX_VALUE, response.getShareCount());
        assertEquals(Integer.MAX_VALUE, response.getTotalAccessCount());
    }
}