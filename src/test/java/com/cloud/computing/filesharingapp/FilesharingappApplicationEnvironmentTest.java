package com.cloud.computing.filesharingapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class FilesharingappApplicationEnvironmentTest {

    private Properties originalSystemProperties;

    @BeforeEach
    void setUp() {
        // Backup original system properties
        originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());
    }

    @AfterEach
    void tearDown() {
        // Restore original system properties
        System.setProperties(originalSystemProperties);
    }

    @Test
    void testLoadEnvironmentVariablesDoesNotThrowException() {
        // When & Then - should not throw exception even if .env file doesn't exist
        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(FilesharingappApplication.class, "loadEnvironmentVariables"));
    }

    @Test
    void testLoadEnvironmentVariablesPreservesExistingSystemProperties() {
        // Given
        String testKey = "TEST_EXISTING_PROPERTY";
        String originalValue = "ORIGINAL_VALUE";
        System.setProperty(testKey, originalValue);

        // When
        ReflectionTestUtils.invokeMethod(FilesharingappApplication.class, "loadEnvironmentVariables");

        // Then - existing property should be preserved
        assertEquals(originalValue, System.getProperty(testKey));
    }

    @Test
    void testLoadEnvironmentVariablesHandlesExceptions() {
        // This test verifies that the method handles exceptions gracefully
        // The actual .env loading is tested through integration tests

        // When & Then - should not throw exception
        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(FilesharingappApplication.class, "loadEnvironmentVariables"));
    }
}