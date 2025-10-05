package com.cloud.computing.filesharingapp;

import com.cloud.computing.filesharingapp.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FilesharingappApplication class.
 * Tests the CommandLineRunner implementation and FileService initialization logic.
 */
@ExtendWith(MockitoExtension.class)
class FilesharingappApplicationUnitTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private FilesharingappApplication application;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(fileService);
    }

    @Test
    void testRunWithFileServicePresent() throws Exception {
        // Given: FileService is available and injected
        ReflectionTestUtils.setField(application, "fileService", fileService);

        // When: run method is called
        application.run();

        // Then: FileService.init() should be called exactly once
        verify(fileService, times(1)).init();
    }

    @Test
    void testRunWithFileServiceNull() throws Exception {
        // Given: FileService is null (not available in context)
        ReflectionTestUtils.setField(application, "fileService", null);

        // When: run method is called
        application.run();

        // Then: No exception should be thrown and no methods should be called
        // This test passes if no exception is thrown
        assertDoesNotThrow(() -> application.run());
    }

    @Test
    void testRunWithFileServicePresentAndArguments() throws Exception {
        // Given: FileService is available and command line arguments are provided
        ReflectionTestUtils.setField(application, "fileService", fileService);
        String[] args = {"--spring.profiles.active=test", "--debug"};

        // When: run method is called with arguments
        application.run(args);

        // Then: FileService.init() should be called regardless of arguments
        verify(fileService, times(1)).init();
    }

    @Test
    void testRunWithFileServiceNullAndArguments() throws Exception {
        // Given: FileService is null and command line arguments are provided
        ReflectionTestUtils.setField(application, "fileService", null);
        String[] args = {"--spring.profiles.active=test", "--debug"};

        // When: run method is called with arguments
        application.run(args);

        // Then: No exception should be thrown
        assertDoesNotThrow(() -> application.run(args));
    }

    @Test
    void testRunWithFileServiceThrowingException() throws Exception {
        // Given: FileService is available but throws exception during init
        ReflectionTestUtils.setField(application, "fileService", fileService);
        doThrow(new RuntimeException("FileService initialization failed")).when(fileService).init();

        // When & Then: Exception should be propagated
        RuntimeException exception = assertThrows(RuntimeException.class, () -> application.run());
        assertEquals("FileService initialization failed", exception.getMessage());
        verify(fileService, times(1)).init();
    }

    @Test
    void testRunWithEmptyArguments() throws Exception {
        // Given: FileService is available and empty arguments array
        ReflectionTestUtils.setField(application, "fileService", fileService);
        String[] emptyArgs = {};

        // When: run method is called with empty arguments
        application.run(emptyArgs);

        // Then: FileService.init() should still be called
        verify(fileService, times(1)).init();
    }

    @Test
    void testRunMultipleCalls() throws Exception {
        // Given: FileService is available
        ReflectionTestUtils.setField(application, "fileService", fileService);

        // When: run method is called multiple times
        application.run();
        application.run();
        application.run();

        // Then: FileService.init() should be called each time
        verify(fileService, times(3)).init();
    }

    @Test
    void testFileServiceAutowiredOptional() {
        // Given: A new instance of the application
        FilesharingappApplication newApplication = new FilesharingappApplication();

        // When: FileService is not injected (remains null)
        Object fileServiceField = ReflectionTestUtils.getField(newApplication, "fileService");

        // Then: FileService should be null and application should handle it gracefully
        assertNull(fileServiceField);
        assertDoesNotThrow(() -> newApplication.run());
    }

    @Test
    void testRunWithNullArgumentsArray() throws Exception {
        // Given: FileService is available
        ReflectionTestUtils.setField(application, "fileService", fileService);

        // When: run method is called with null arguments
        application.run((String[]) null);

        // Then: FileService.init() should be called without issues
        verify(fileService, times(1)).init();
    }

    @Test
    void testApplicationClassStructure() {
        // Given: The application class
        Class<FilesharingappApplication> appClass = FilesharingappApplication.class;

        // Then: Verify class implements CommandLineRunner
        assertTrue(CommandLineRunner.class.isAssignableFrom(appClass));

        // And: Verify class has required annotations
        assertTrue(appClass.isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class));
        assertTrue(appClass.isAnnotationPresent(org.springframework.scheduling.annotation.EnableScheduling.class));
    }

    @Test
    void testMainMethodExists() {
        // Given: The application class
        Class<FilesharingappApplication> appClass = FilesharingappApplication.class;

        // When & Then: Verify main method exists and is public static
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method mainMethod = appClass.getMethod("main", String[].class);
            assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()));
            assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
        });
    }
}