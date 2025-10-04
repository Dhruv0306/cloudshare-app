package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

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
    void testUserEquality() {
        User user1 = new User("testuser", "test@example.com", "password123");
        User user2 = new User("testuser", "test@example.com", "password123");
        
        // Note: Since we don't override equals/hashCode, these will be different objects
        assertNotEquals(user1, user2);
    }
}