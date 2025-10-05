package com.cloud.computing.filesharingapp;

import com.cloud.computing.filesharingapp.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;

import static org.mockito.Mockito.verify;

@SpringBootTest
@TestPropertySource(properties = {
    "logging.file.path=${java.io.tmpdir}/test-logs",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "JWT_SECRET=testSecretKey123456789012345678901234567890",
    "app.jwtSecret=testSecretKey123456789012345678901234567890"
})
class FilesharingappApplicationIntegrationTest {

    @MockitoBean
    private FileService fileService;

    @TempDir
    Path tempDir;

    @Test
    void contextLoads() {
        // This test ensures that the Spring context loads successfully
        // with all the new changes in FilesharingappApplication
    }

    @Test
    void testApplicationStartupCallsFileServiceInit() throws Exception {
        // Given - Spring Boot application context is loaded
        
        // Then - FileService.init() should have been called during startup
        verify(fileService).init();
    }
}