package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class FileControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;



    private MockMvc mockMvc;
    private User testUser;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Create test user
        testUser = new User("testuser", "test@example.com", passwordEncoder.encode("password123"));
        testUser.setEmailVerified(true);
        testUser.setAccountStatus(AccountStatus.ACTIVE);
        testUser = userRepository.save(testUser);

        // Create authentication
        UserPrincipal userPrincipal = UserPrincipal.create(testUser);
        authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
    }

    @Test
    void testUploadFile() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello World".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("test.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.fileSize").value(11));
    }

    @Test
    void testUploadFileUnauthorized() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello World".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/api/files/upload")
                .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetUserFiles() throws Exception {
        // Given
        FileEntity file1 = new FileEntity("uuid1_test1.txt", "test1.txt", "text/plain", 1024L, "/path1", testUser);
        FileEntity file2 = new FileEntity("uuid2_test2.txt", "test2.txt", "text/plain", 2048L, "/path2", testUser);
        fileRepository.save(file1);
        fileRepository.save(file2);

        // When & Then
        mockMvc.perform(get("/api/files")
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].originalFileName").value("test1.txt"))
                .andExpect(jsonPath("$[1].originalFileName").value("test2.txt"));
    }

    @Test
    void testGetUserFilesUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetUserFileById() throws Exception {
        // Given
        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path", testUser);
        file = fileRepository.save(file);

        // When & Then
        mockMvc.perform(get("/api/files/" + file.getId())
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("test.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"));
    }

    @Test
    void testGetUserFileByIdNotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/files/999")
                .with(authentication(authentication)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteUserFile() throws Exception {
        // Given
        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path", testUser);
        file = fileRepository.save(file);

        // When & Then
        mockMvc.perform(delete("/api/files/" + file.getId())
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string("File deleted successfully"));
    }

    @Test
    void testDeleteUserFileNotFound() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/files/999")
                .with(authentication(authentication)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Could not delete file: File not found or access denied"));
    }

    @Test
    void testFileIsolationBetweenUsers() throws Exception {
        // Given
        User otherUser = new User("otheruser", "other@example.com", passwordEncoder.encode("password123"));
        otherUser = userRepository.save(otherUser);

        FileEntity otherUserFile = new FileEntity("uuid_other.txt", "other.txt", "text/plain", 1024L, "/path", otherUser);
        otherUserFile = fileRepository.save(otherUserFile);

        // When & Then - testUser should not be able to access otherUser's file
        mockMvc.perform(get("/api/files/" + otherUserFile.getId())
                .with(authentication(authentication)))
                .andExpect(status().isNotFound());

        // When & Then - testUser should not be able to delete otherUser's file
        mockMvc.perform(delete("/api/files/" + otherUserFile.getId())
                .with(authentication(authentication)))
                .andExpect(status().isBadRequest());
    }
}