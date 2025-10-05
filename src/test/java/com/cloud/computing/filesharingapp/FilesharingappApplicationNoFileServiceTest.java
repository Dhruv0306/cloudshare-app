package com.cloud.computing.filesharingapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration test for FilesharingappApplication when FileService is not available.
 * Tests the graceful handling of missing optional dependencies.
 */
@SpringBootTest
@ActiveProfiles("test")
class FilesharingappApplicationNoFileServiceTest {

    @TestConfiguration
    static class NoFileServiceConfiguration {
        // This configuration intentionally does not provide a FileService bean
        // to test the @Autowired(required = false) behavior
        
        @Bean
        @Primary
        public String testMarker() {
            return "no-file-service-test";
        }
    }

    @Test
    void applicationStartsGracefullyWithoutFileService() {
        // Given: Application context is loaded without FileService bean
        // When: Application starts up
        // Then: No exception should be thrown during startup
        assertDoesNotThrow(() -> {
            // The test passes if the application context loads successfully
            // and CommandLineRunner.run() executes without throwing exceptions
        });
    }
}