package com.cloud.computing.filesharingapp;

import com.cloud.computing.filesharingapp.config.TestEmailConfig;
import com.cloud.computing.filesharingapp.dto.LoginRequest;
import com.cloud.computing.filesharingapp.dto.SignupRequest;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@Import(TestEmailConfig.class)
class FilesharingappApplicationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void contextLoads() {
        // Test that the application context loads successfully
    }

    @Test
    void testCompleteUserJourney() throws Exception {
        // 1. Sign up a new user
        SignupRequest signupRequest = new SignupRequest("journeyuser", "journey@example.com", "Password123");
        
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully! Please check your email for verification code."));

        // 2. Manually verify the user (simulate email verification)
        // In a real scenario, user would click email link, but for testing we'll verify directly
        User user = userRepository.findByUsername("journeyuser").orElseThrow();
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        // 3. Login with the user
        LoginRequest loginRequest = new LoginRequest("journeyuser", "Password123");
        
        MvcResult loginResult = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        // Extract JWT token
        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("accessToken").asText();

        // 4. Upload a file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "journey-test.txt",
                "text/plain",
                "This is a test file for the complete user journey".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("journey-test.txt"))
                .andReturn();

        // Extract file ID
        String uploadResponseBody = uploadResult.getResponse().getContentAsString();
        Long fileId = objectMapper.readTree(uploadResponseBody).get("id").asLong();

        // 4. List user files
        mockMvc.perform(get("/api/files")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalFileName").value("journey-test.txt"));

        // 5. Get specific file
        mockMvc.perform(get("/api/files/" + fileId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("journey-test.txt"));

        // 6. Delete the file
        mockMvc.perform(delete("/api/files/" + fileId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("File deleted successfully"));

        // 7. Verify file is deleted
        mockMvc.perform(get("/api/files")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        // Test that endpoints are protected
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/files/upload"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/files/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogTestEndpoint() throws Exception {
        // Test the log test endpoint
        mockMvc.perform(get("/api/test/logs"))
                .andExpect(status().isOk())
                .andExpect(content().string("Log messages sent! Check your console and log files."));
    }
}