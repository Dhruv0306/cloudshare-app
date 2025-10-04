package com.cloud.computing.filesharingapp.util;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.security.UserPrincipal;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class TestUtils {

    public static User createTestUser(String username, String email, String password) {
        User user = new User(username, email, password);
        user.setId(1L);
        return user;
    }

    public static FileEntity createTestFile(String fileName, String originalFileName, User owner) {
        return new FileEntity(
                fileName,
                originalFileName,
                "text/plain",
                1024L,
                "/test/path/" + fileName,
                owner
        );
    }

    public static MockMultipartFile createTestMultipartFile(String filename, String content) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                content.getBytes()
        );
    }

    public static Authentication createAuthentication(User user) {
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities()
        );
    }

    public static String createJwtToken(String username) {
        // This is a mock JWT token for testing purposes
        // In real tests, you would use the actual JwtUtils
        return "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTYzMjE0NDAwMCwiZXhwIjoxNjMyMTQ3NjAwfQ.test-signature";
    }
}