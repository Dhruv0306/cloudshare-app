package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(user);
        assertNotNull(user.getCreatedAt());
        assertTrue(user.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertFalse(user.isEmailVerified());
        assertEquals(AccountStatus.PENDING, user.getAccountStatus());
    }

    @Test
    void testParameterizedConstructor() {
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";
        
        User user = new User(username, email, password);
        
        assertEquals(username, user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals(password, user.getPassword());
        assertNotNull(user.getCreatedAt());
        assertFalse(user.isEmailVerified());
        assertEquals(AccountStatus.PENDING, user.getAccountStatus());
    }

    @Test
    void testSettersAndGetters() {
        Long id = 1L;
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";
        LocalDateTime createdAt = LocalDateTime.now();

        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setCreatedAt(createdAt);

        assertEquals(id, user.getId());
        assertEquals(username, user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals(password, user.getPassword());
        assertEquals(createdAt, user.getCreatedAt());
    }

    @Test
    void testEmailVerificationSettersAndGetters() {
        assertFalse(user.isEmailVerified());
        
        user.setEmailVerified(true);
        assertTrue(user.isEmailVerified());
        
        user.setEmailVerified(false);
        assertFalse(user.isEmailVerified());
    }

    @Test
    void testAccountStatusSettersAndGetters() {
        assertEquals(AccountStatus.PENDING, user.getAccountStatus());
        
        user.setAccountStatus(AccountStatus.ACTIVE);
        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        
        user.setAccountStatus(AccountStatus.SUSPENDED);
        assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
    }

    @Test
    void testFilesSettersAndGetters() {
        assertNull(user.getFiles());
        
        List<FileEntity> files = new ArrayList<>();
        user.setFiles(files);
        
        assertEquals(files, user.getFiles());
        assertTrue(user.getFiles().isEmpty());
    }

    @Test
    void testEmailVerificationsSettersAndGetters() {
        assertNull(user.getEmailVerifications());
        
        List<EmailVerification> emailVerifications = new ArrayList<>();
        user.setEmailVerifications(emailVerifications);
        
        assertEquals(emailVerifications, user.getEmailVerifications());
        assertTrue(user.getEmailVerifications().isEmpty());
    }

    @Test
    void testUserEquality() {
        User user1 = new User("testuser", "test@example.com", "password123");
        User user2 = new User("testuser", "test@example.com", "password123");
        
        // Note: Since we don't override equals/hashCode, these will be different objects
        assertNotEquals(user1, user2);
    }

    @Test
    void testUserWithAllFields() {
        User user = new User("testuser", "test@example.com", "password123");
        user.setId(1L);
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        
        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("password123", user.getPassword());
        assertTrue(user.isEmailVerified());
        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void testDefaultAccountStatusAndEmailVerification() {
        User newUser = new User("newuser", "new@example.com", "newpass");
        
        assertEquals(AccountStatus.PENDING, newUser.getAccountStatus());
        assertFalse(newUser.isEmailVerified());
    }

    @Test
    void testCreatedAtIsSetOnConstruction() {
        LocalDateTime beforeCreation = LocalDateTime.now();
        User user = new User("testuser", "test@example.com", "password123");
        LocalDateTime afterCreation = LocalDateTime.now();
        
        assertNotNull(user.getCreatedAt());
        assertTrue(user.getCreatedAt().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(user.getCreatedAt().isBefore(afterCreation.plusSeconds(1)));
    }
}