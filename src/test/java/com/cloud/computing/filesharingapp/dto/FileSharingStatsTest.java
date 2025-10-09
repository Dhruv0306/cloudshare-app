package com.cloud.computing.filesharingapp.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FileSharingStats DTO.
 * 
 * <p>Tests the FileSharingStats data transfer object including:
 * <ul>
 *   <li>Default constructor initialization</li>
 *   <li>Getter and setter functionality</li>
 *   <li>Sharing statistics calculations</li>
 *   <li>Edge cases and boundary conditions</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
class FileSharingStatsTest {

    private FileSharingStats stats;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        stats = new FileSharingStats();
        testTime = LocalDateTime.now();
    }

    /**
     * Test default constructor sets appropriate default values
     */
    @Test
    void testDefaultConstructor() {
        // When
        FileSharingStats newStats = new FileSharingStats();

        // Then
        assertNull(newStats.getFileId());
        assertFalse(newStats.isShared());
        assertEquals(0, newStats.getTotalShares());
        assertEquals(0, newStats.getActiveShares());
        assertEquals(0, newStats.getTotalAccessCount());
        assertNull(newStats.getLastSharedAt());
        assertNull(newStats.getLastAccessedAt());
        assertFalse(newStats.isHasActiveShares());
    }

    /**
     * Test all getter and setter methods
     */
    @Test
    void testGettersAndSetters() {
        // Given
        Long fileId = 123L;
        LocalDateTime sharedTime = testTime.minusHours(2);
        LocalDateTime accessedTime = testTime.minusMinutes(30);

        // When
        stats.setFileId(fileId);
        stats.setShared(true);
        stats.setTotalShares(5);
        stats.setActiveShares(3);
        stats.setTotalAccessCount(25);
        stats.setLastSharedAt(sharedTime);
        stats.setLastAccessedAt(accessedTime);
        stats.setHasActiveShares(true);

        // Then
        assertEquals(fileId, stats.getFileId());
        assertTrue(stats.isShared());
        assertEquals(5, stats.getTotalShares());
        assertEquals(3, stats.getActiveShares());
        assertEquals(25, stats.getTotalAccessCount());
        assertEquals(sharedTime, stats.getLastSharedAt());
        assertEquals(accessedTime, stats.getLastAccessedAt());
        assertTrue(stats.isHasActiveShares());
    }

    /**
     * Test statistics for file with no shares
     */
    @Test
    void testNoSharesStatistics() {
        // Given
        Long fileId = 456L;

        // When
        stats.setFileId(fileId);
        // Leave other fields as defaults

        // Then
        assertEquals(fileId, stats.getFileId());
        assertFalse(stats.isShared());
        assertEquals(0, stats.getTotalShares());
        assertEquals(0, stats.getActiveShares());
        assertEquals(0, stats.getTotalAccessCount());
        assertNull(stats.getLastSharedAt());
        assertNull(stats.getLastAccessedAt());
        assertFalse(stats.isHasActiveShares());
    }

    /**
     * Test statistics for file with inactive shares
     */
    @Test
    void testInactiveSharesStatistics() {
        // Given
        Long fileId = 789L;
        LocalDateTime sharedTime = testTime.minusDays(7);

        // When - File has shares but none are active
        stats.setFileId(fileId);
        stats.setShared(true);
        stats.setTotalShares(3);
        stats.setActiveShares(0); // No active shares
        stats.setTotalAccessCount(10);
        stats.setLastSharedAt(sharedTime);
        stats.setLastAccessedAt(testTime.minusDays(1));
        stats.setHasActiveShares(false);

        // Then
        assertEquals(fileId, stats.getFileId());
        assertTrue(stats.isShared());
        assertEquals(3, stats.getTotalShares());
        assertEquals(0, stats.getActiveShares());
        assertEquals(10, stats.getTotalAccessCount());
        assertEquals(sharedTime, stats.getLastSharedAt());
        assertFalse(stats.isHasActiveShares());
    }

    /**
     * Test statistics for highly accessed file
     */
    @Test
    void testHighlyAccessedFileStatistics() {
        // Given
        Long fileId = 999L;
        LocalDateTime recentShare = testTime.minusMinutes(15);
        LocalDateTime recentAccess = testTime.minusMinutes(5);

        // When - File with many shares and high access count
        stats.setFileId(fileId);
        stats.setShared(true);
        stats.setTotalShares(50);
        stats.setActiveShares(35);
        stats.setTotalAccessCount(1000);
        stats.setLastSharedAt(recentShare);
        stats.setLastAccessedAt(recentAccess);
        stats.setHasActiveShares(true);

        // Then
        assertEquals(fileId, stats.getFileId());
        assertTrue(stats.isShared());
        assertEquals(50, stats.getTotalShares());
        assertEquals(35, stats.getActiveShares());
        assertEquals(1000, stats.getTotalAccessCount());
        assertEquals(recentShare, stats.getLastSharedAt());
        assertEquals(recentAccess, stats.getLastAccessedAt());
        assertTrue(stats.isHasActiveShares());
    }

    /**
     * Test edge cases with boundary values
     */
    @Test
    void testEdgeCases() {
        // Given
        Long fileId = 0L;

        // When - Test with zero and maximum values
        stats.setFileId(fileId);
        stats.setTotalShares(Integer.MAX_VALUE);
        stats.setActiveShares(Integer.MAX_VALUE);
        stats.setTotalAccessCount(Integer.MAX_VALUE);

        // Then
        assertEquals(fileId, stats.getFileId());
        assertEquals(Integer.MAX_VALUE, stats.getTotalShares());
        assertEquals(Integer.MAX_VALUE, stats.getActiveShares());
        assertEquals(Integer.MAX_VALUE, stats.getTotalAccessCount());
    }

    /**
     * Test consistency between shared flag and share counts
     */
    @Test
    void testSharedFlagConsistency() {
        // Given
        Long fileId = 111L;

        // When - File is marked as shared but has no shares
        stats.setFileId(fileId);
        stats.setShared(true);
        stats.setTotalShares(0);
        stats.setActiveShares(0);
        stats.setHasActiveShares(false);

        // Then - This represents an inconsistent state that might occur during transitions
        assertTrue(stats.isShared());
        assertEquals(0, stats.getTotalShares());
        assertEquals(0, stats.getActiveShares());
        assertFalse(stats.isHasActiveShares());

        // When - File is not marked as shared but has shares
        stats.setShared(false);
        stats.setTotalShares(2);
        stats.setActiveShares(1);
        stats.setHasActiveShares(true);

        // Then - Another inconsistent state
        assertFalse(stats.isShared());
        assertEquals(2, stats.getTotalShares());
        assertEquals(1, stats.getActiveShares());
        assertTrue(stats.isHasActiveShares());
    }

    /**
     * Test null handling for optional fields
     */
    @Test
    void testNullHandling() {
        // Given
        Long fileId = 222L;

        // When
        stats.setFileId(fileId);
        stats.setShared(true);
        stats.setTotalShares(1);
        stats.setActiveShares(1);
        stats.setTotalAccessCount(5);
        stats.setLastSharedAt(null);
        stats.setLastAccessedAt(null);
        stats.setHasActiveShares(true);

        // Then
        assertEquals(fileId, stats.getFileId());
        assertTrue(stats.isShared());
        assertEquals(1, stats.getTotalShares());
        assertEquals(1, stats.getActiveShares());
        assertEquals(5, stats.getTotalAccessCount());
        assertNull(stats.getLastSharedAt());
        assertNull(stats.getLastAccessedAt());
        assertTrue(stats.isHasActiveShares());
    }

    /**
     * Test temporal relationships between share and access times
     */
    @Test
    void testTemporalRelationships() {
        // Given
        Long fileId = 333L;
        LocalDateTime shareTime = testTime.minusHours(3);
        LocalDateTime accessTime = testTime.minusHours(1);

        // When - Access time is after share time (normal case)
        stats.setFileId(fileId);
        stats.setShared(true);
        stats.setTotalShares(1);
        stats.setActiveShares(1);
        stats.setTotalAccessCount(3);
        stats.setLastSharedAt(shareTime);
        stats.setLastAccessedAt(accessTime);
        stats.setHasActiveShares(true);

        // Then
        assertTrue(stats.getLastAccessedAt().isAfter(stats.getLastSharedAt()));

        // When - Access time is before share time (edge case)
        LocalDateTime earlierAccessTime = testTime.minusHours(5);
        stats.setLastAccessedAt(earlierAccessTime);

        // Then
        assertTrue(stats.getLastAccessedAt().isBefore(stats.getLastSharedAt()));
    }

    /**
     * Test statistics update scenarios
     */
    @Test
    void testStatisticsUpdates() {
        // Given - Initial state
        Long fileId = 444L;
        stats.setFileId(fileId);
        stats.setShared(false);
        stats.setTotalShares(0);
        stats.setActiveShares(0);
        stats.setTotalAccessCount(0);
        stats.setHasActiveShares(false);

        // When - First share is created
        stats.setShared(true);
        stats.setTotalShares(1);
        stats.setActiveShares(1);
        stats.setLastSharedAt(testTime);
        stats.setHasActiveShares(true);

        // Then
        assertTrue(stats.isShared());
        assertEquals(1, stats.getTotalShares());
        assertEquals(1, stats.getActiveShares());
        assertTrue(stats.isHasActiveShares());

        // When - Share is accessed
        stats.setTotalAccessCount(1);
        stats.setLastAccessedAt(testTime.plusMinutes(30));

        // Then
        assertEquals(1, stats.getTotalAccessCount());
        assertNotNull(stats.getLastAccessedAt());

        // When - Share is revoked
        stats.setActiveShares(0);
        stats.setHasActiveShares(false);

        // Then
        assertEquals(1, stats.getTotalShares()); // Total shares remains
        assertEquals(0, stats.getActiveShares()); // But active shares is 0
        assertFalse(stats.isHasActiveShares());
    }
}