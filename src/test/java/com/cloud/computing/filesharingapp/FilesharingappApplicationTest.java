package com.cloud.computing.filesharingapp;

import com.cloud.computing.filesharingapp.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilesharingappApplicationTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private FilesharingappApplication application;

    @TempDir
    Path tempDir;

    private Properties originalSystemProperties;

    @BeforeEach
    void setUp() {
        // Backup original system properties
        originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());
    }

    @Test
    void testRunWithFileService() throws Exception {
        // Given
        doNothing().when(fileService).init();

        // When
        application.run();

        // Then
        verify(fileService).init();
    }

    @Test
    void testRunWithNullFileService() throws Exception {
        // Given
        ReflectionTestUtils.setField(application, "fileService", null);

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> application.run());
    }

    @Test
    void testRunWithFileServiceException() throws Exception {
        // Given
        doThrow(new RuntimeException("Init failed")).when(fileService).init();

        // When & Then
        assertThrows(RuntimeException.class, () -> application.run());
    }

    @Test
    void testCleanupLogFilesCreatesDirectoryWhenNotExists() throws Exception {
        // Given
        String logPath = tempDir.resolve("logs").toString();
        System.setProperty("logging.file.path", logPath);

        // When
        ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles");

        // Then
        assertTrue(Files.exists(Paths.get(logPath)));
    }

    @Test
    void testCleanupLogFilesWithNoLogFiles() throws Exception {
        // Given
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        System.setProperty("logging.file.path", logsDir.toString());

        // When
        ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles");

        // Then
        assertTrue(Files.exists(logsDir));
    }

    @Test
    void testCleanupLogFilesKeepsRecentFiles() throws Exception {
        // Given
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        System.setProperty("logging.file.path", logsDir.toString());

        // Create 5 log files (less than max of 10)
        for (int i = 0; i < 5; i++) {
            Path logFile = logsDir.resolve("app." + i + ".log");
            Files.createFile(logFile);
            // Set different modification times
            logFile.toFile().setLastModified(System.currentTimeMillis() - (i * 1000));
        }

        // When
        ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles");

        // Then
        assertEquals(5, Files.list(logsDir).count());
    }

    @Test
    void testCleanupLogFilesDeletesOldFiles() throws Exception {
        // Given
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        System.setProperty("logging.file.path", logsDir.toString());

        // Create 15 log files (more than max of 10)
        for (int i = 0; i < 15; i++) {
            Path logFile = logsDir.resolve("app." + i + ".log");
            Files.createFile(logFile);
            // Set different modification times (older files have smaller timestamps)
            logFile.toFile().setLastModified(System.currentTimeMillis() - ((15 - i) * 1000));
        }

        // When
        ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles");

        // Then
        assertEquals(10, Files.list(logsDir).count());
    }

    @Test
    void testCleanupLogFilesHandlesDifferentLogFileTypes() throws Exception {
        // Given
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        System.setProperty("logging.file.path", logsDir.toString());

        // Create different types of log files
        Files.createFile(logsDir.resolve("app.log"));
        Files.createFile(logsDir.resolve("app.log.1"));
        Files.createFile(logsDir.resolve("app.log.2"));
        Files.createFile(logsDir.resolve("app.log.gz"));
        Files.createFile(logsDir.resolve("other.txt")); // Should be ignored

        // When
        ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles");

        // Then
        assertTrue(Files.exists(logsDir.resolve("app.log")));
        assertTrue(Files.exists(logsDir.resolve("app.log.1")));
        assertTrue(Files.exists(logsDir.resolve("app.log.2")));
        assertTrue(Files.exists(logsDir.resolve("app.log.gz")));
        assertTrue(Files.exists(logsDir.resolve("other.txt")));
    }

    @Test
    void testCleanupLogFilesHandlesIOException() throws Exception {
        // Given
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        System.setProperty("logging.file.path", logsDir.toString());

        // Create a log file and make it read-only to simulate deletion failure
        Path logFile = logsDir.resolve("readonly.log");
        Files.createFile(logFile);
        logFile.toFile().setReadOnly();

        // Create more files to trigger cleanup
        for (int i = 0; i < 12; i++) {
            Path file = logsDir.resolve("app." + i + ".log");
            Files.createFile(file);
            file.toFile().setLastModified(System.currentTimeMillis() - (i * 1000));
        }

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles"));
    }

    @Test
    void testCleanupLogFilesUsesDefaultLogPath() throws Exception {
        // Given
        System.clearProperty("logging.file.path");

        // When
        ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles");

        // Then - should use default "logs" directory
        // This test mainly ensures no exception is thrown when using default path
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles"));
    }

    @Test
    void testCleanupLogFilesHandlesGeneralException() throws Exception {
        // Given
        System.setProperty("logging.file.path", "/invalid/path/that/cannot/be/created");

        // When & Then - should not throw exception but handle it gracefully
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(application, "cleanupLogFiles"));
    }
}