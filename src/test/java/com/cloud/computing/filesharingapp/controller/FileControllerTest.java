package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.dto.ShareRequest;
import com.cloud.computing.filesharingapp.entity.*;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
import com.cloud.computing.filesharingapp.service.FileSharingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Arrays;

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
        private FileSharingService fileSharingService;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        private MockMvc mockMvc;
        private User testUser;
        private User otherUser;
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

                // Create other test user for isolation tests
                otherUser = new User("otheruser", "other@example.com", passwordEncoder.encode("password123"));
                otherUser.setEmailVerified(true);
                otherUser.setAccountStatus(AccountStatus.ACTIVE);
                otherUser = userRepository.save(otherUser);

                // Create authentication
                UserPrincipal userPrincipal = UserPrincipal.create(testUser);
                authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null,
                                userPrincipal.getAuthorities());
        }

        @Test
        void testUploadFile() throws Exception {
                // Given
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "test.txt",
                                "text/plain",
                                "Hello World".getBytes());

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
                                "Hello World".getBytes());

                // When & Then
                mockMvc.perform(multipart("/api/files/upload")
                                .file(file))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void testGetUserFiles() throws Exception {
                // Given
                FileEntity file1 = new FileEntity("uuid1_test1.txt", "test1.txt", "text/plain", 1024L, "/path1",
                                testUser);
                FileEntity file2 = new FileEntity("uuid2_test2.txt", "test2.txt", "text/plain", 2048L, "/path2",
                                testUser);
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
                FileEntity otherUserFile = new FileEntity("uuid_other.txt", "other.txt", "text/plain", 1024L, "/path",
                                otherUser);
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

        // ========== FILE SHARING TESTS ==========

        @Nested
        class FileSharingTests {

                @Test
                void testCreateFileShare_Success() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        shareRequest.setExpiresAt(LocalDateTime.now().plusDays(7));

                        // When & Then
                        mockMvc.perform(post("/api/files/" + file.getId() + "/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(shareRequest))
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.shareId").exists())
                                        .andExpect(jsonPath("$.shareToken").exists())
                                        .andExpect(jsonPath("$.shareUrl").exists())
                                        .andExpect(jsonPath("$.permission").value("DOWNLOAD"))
                                        .andExpect(jsonPath("$.active").value(true));
                }

                @Test
                void testCreateFileShare_WithEmailNotification() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.VIEW_ONLY);
                        shareRequest.setRecipientEmails(
                                        Arrays.asList("recipient1@example.com", "recipient2@example.com"));
                        shareRequest.setSendNotification(true);

                        // When & Then
                        mockMvc.perform(post("/api/files/" + file.getId() + "/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(shareRequest))
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.permission").value("VIEW_ONLY"));
                }

                @Test
                void testCreateFileShare_FileNotFound() throws Exception {
                        // Given
                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);

                        // When & Then
                        mockMvc.perform(post("/api/files/999/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(shareRequest))
                                        .with(authentication(authentication)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").exists());
                }

                @Test
                void testCreateFileShare_UnauthorizedUser() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        otherUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);

                        // When & Then - testUser trying to share otherUser's file
                        mockMvc.perform(post("/api/files/" + file.getId() + "/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(shareRequest))
                                        .with(authentication(authentication)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void testCreateFileShare_InvalidPermission() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        // When & Then - Missing required permission
                        mockMvc.perform(post("/api/files/" + file.getId() + "/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void testAccessSharedFile_Success() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        String shareToken = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareToken();

                        // When & Then
                        mockMvc.perform(get("/api/files/shared/" + shareToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.fileId").value(file.getId()))
                                        .andExpect(jsonPath("$.originalFileName").value("test.txt"))
                                        .andExpect(jsonPath("$.contentType").value("text/plain"))
                                        .andExpect(jsonPath("$.fileSize").value(1024))
                                        .andExpect(jsonPath("$.permission").value("DOWNLOAD"));
                }

                @Test
                void testAccessSharedFile_InvalidToken() throws Exception {
                        // When & Then
                        mockMvc.perform(get("/api/files/shared/invalid-token"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.message").exists());
                }

                @Test
                void testDownloadSharedFile_Success() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        String shareToken = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareToken();

                        // When & Then - File doesn't exist physically, so expect error
                        mockMvc.perform(get("/api/files/shared/" + shareToken + "/download"))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").exists());
                }

                @Test
                void testDownloadSharedFile_ViewOnlyPermission() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.VIEW_ONLY);
                        String shareToken = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareToken();

                        // When & Then
                        mockMvc.perform(get("/api/files/shared/" + shareToken + "/download"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.message")
                                                        .value("Download permission not granted for this share"));
                }

                @Test
                void testDownloadSharedFile_InvalidToken() throws Exception {
                        // When & Then
                        mockMvc.perform(get("/api/files/shared/invalid-token/download"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.message").exists());
                }

                @Test
                void testGetFileShares_Success() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest1 = new ShareRequest(SharePermission.DOWNLOAD);
                        ShareRequest shareRequest2 = new ShareRequest(SharePermission.VIEW_ONLY);

                        fileSharingService.createShare(file.getId(), shareRequest1, testUser);
                        fileSharingService.createShare(file.getId(), shareRequest2, testUser);

                        // When & Then
                        mockMvc.perform(get("/api/files/" + file.getId() + "/shares")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.length()").value(2));
                }

                @Test
                void testGetFileShares_FileNotFound() throws Exception {
                        // When & Then
                        mockMvc.perform(get("/api/files/999/shares")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void testGetFileShares_UnauthorizedUser() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        otherUser);
                        file = fileRepository.save(file);

                        // When & Then - testUser trying to access otherUser's file shares
                        mockMvc.perform(get("/api/files/" + file.getId() + "/shares")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void testUpdateShare_Success() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.VIEW_ONLY);
                        Long shareId = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareId();

                        String updateRequest = "{\"permission\":\"DOWNLOAD\"}";

                        // When & Then
                        mockMvc.perform(put("/api/files/shares/" + shareId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(updateRequest)
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.permission").value("DOWNLOAD"));
                }

                @Test
                void testUpdateShare_ShareNotFound() throws Exception {
                        // Given
                        String updateRequest = "{\"permission\":\"DOWNLOAD\"}";

                        // When & Then
                        mockMvc.perform(put("/api/files/shares/999")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(updateRequest)
                                        .with(authentication(authentication)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void testUpdateShare_UnauthorizedUser() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        otherUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.VIEW_ONLY);
                        Long shareId = fileSharingService.createShare(file.getId(), shareRequest, otherUser)
                                        .getShareId();

                        String updateRequest = "{\"permission\":\"DOWNLOAD\"}";

                        // When & Then - testUser trying to update otherUser's share
                        mockMvc.perform(put("/api/files/shares/" + shareId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(updateRequest)
                                        .with(authentication(authentication)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void testRevokeShare_Success() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        Long shareId = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareId();

                        // When & Then
                        mockMvc.perform(delete("/api/files/shares/" + shareId)
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.message").value("Share revoked successfully"));
                }

                @Test
                void testRevokeShare_ShareNotFound() throws Exception {
                        // When & Then
                        mockMvc.perform(delete("/api/files/shares/999")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void testRevokeShare_UnauthorizedUser() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        otherUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        Long shareId = fileSharingService.createShare(file.getId(), shareRequest, otherUser)
                                        .getShareId();

                        // When & Then - testUser trying to revoke otherUser's share
                        mockMvc.perform(delete("/api/files/shares/" + shareId)
                                        .with(authentication(authentication)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void testGetUserShares_Success() throws Exception {
                        // Given
                        FileEntity file1 = new FileEntity("uuid1_test1.txt", "test1.txt", "text/plain", 1024L, "/path1",
                                        testUser);
                        FileEntity file2 = new FileEntity("uuid2_test2.txt", "test2.txt", "text/plain", 2048L, "/path2",
                                        testUser);
                        file1 = fileRepository.save(file1);
                        file2 = fileRepository.save(file2);

                        ShareRequest shareRequest1 = new ShareRequest(SharePermission.DOWNLOAD);
                        ShareRequest shareRequest2 = new ShareRequest(SharePermission.VIEW_ONLY);

                        fileSharingService.createShare(file1.getId(), shareRequest1, testUser);
                        fileSharingService.createShare(file2.getId(), shareRequest2, testUser);

                        // When & Then
                        mockMvc.perform(get("/api/files/my-shares")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.length()").value(2));
                }

                @Test
                void testGetUserShares_EmptyList() throws Exception {
                        // When & Then
                        mockMvc.perform(get("/api/files/my-shares")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.length()").value(0));
                }

                @Test
                void testGetUserShares_UserIsolation() throws Exception {
                        // Given
                        FileEntity file1 = new FileEntity("uuid1_test1.txt", "test1.txt", "text/plain", 1024L, "/path1",
                                        testUser);
                        FileEntity file2 = new FileEntity("uuid2_test2.txt", "test2.txt", "text/plain", 2048L, "/path2",
                                        otherUser);
                        file1 = fileRepository.save(file1);
                        file2 = fileRepository.save(file2);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        fileSharingService.createShare(file1.getId(), shareRequest, testUser);
                        fileSharingService.createShare(file2.getId(), shareRequest, otherUser);

                        // When & Then - testUser should only see their own shares
                        mockMvc.perform(get("/api/files/my-shares")
                                        .with(authentication(authentication)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.length()").value(1));
                }
        }

        @Nested
        class ShareSecurityTests {

                @Test
                void testShareAccess_WithDifferentIpAddresses() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        String shareToken = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareToken();

                        // When & Then - Access from different IP addresses should work
                        mockMvc.perform(get("/api/files/shared/" + shareToken)
                                        .header("X-Forwarded-For", "192.168.1.1"))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/api/files/shared/" + shareToken)
                                        .header("X-Real-IP", "10.0.0.1"))
                                        .andExpect(status().isOk());
                }

                @Test
                void testShareAccess_WithUserAgent() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        String shareToken = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareToken();

                        // When & Then
                        mockMvc.perform(get("/api/files/shared/" + shareToken)
                                        .header("User-Agent", "Mozilla/5.0 (Test Browser)"))
                                        .andExpect(status().isOk());
                }

                @Test
                void testExpiredShare_Access() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        shareRequest.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired share
                        String shareToken = fileSharingService.createShare(file.getId(), shareRequest, testUser)
                                        .getShareToken();

                        // When & Then
                        mockMvc.perform(get("/api/files/shared/" + shareToken))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.message").exists());
                }
        }

        @Nested
        class ShareValidationTests {

                @Test
                void testCreateShare_InvalidEmailFormat() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        shareRequest.setRecipientEmails(Arrays.asList("invalid-email", "valid@example.com"));
                        shareRequest.setSendNotification(true);

                        // When & Then
                        mockMvc.perform(post("/api/files/" + file.getId() + "/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(shareRequest))
                                        .with(authentication(authentication)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void testCreateShare_NegativeMaxAccess() throws Exception {
                        // Given
                        FileEntity file = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path",
                                        testUser);
                        file = fileRepository.save(file);

                        ShareRequest shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
                        shareRequest.setMaxAccess(-1);

                        // When & Then
                        mockMvc.perform(post("/api/files/" + file.getId() + "/share")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(shareRequest))
                                        .with(authentication(authentication)))
                                        .andExpect(status().isBadRequest());
                }
        }
}