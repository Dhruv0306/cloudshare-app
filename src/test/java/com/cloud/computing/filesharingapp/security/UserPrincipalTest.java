package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class UserPrincipalTest {

    @Test
    void testCreateFromUser() {
        // Given
        User user = new User("testuser", "test@example.com", "password123");
        user.setId(1L);

        // When
        UserPrincipal userPrincipal = UserPrincipal.create(user);

        // Then
        assertEquals(1L, userPrincipal.getId());
        assertEquals("testuser", userPrincipal.getUsername());
        assertEquals("test@example.com", userPrincipal.getEmail());
        assertEquals("password123", userPrincipal.getPassword());
    }

    @Test
    void testConstructor() {
        // Given
        Long id = 1L;
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";

        // When
        UserPrincipal userPrincipal = new UserPrincipal(id, username, email, password);

        // Then
        assertEquals(id, userPrincipal.getId());
        assertEquals(username, userPrincipal.getUsername());
        assertEquals(email, userPrincipal.getEmail());
        assertEquals(password, userPrincipal.getPassword());
    }

    @Test
    void testGetAuthorities() {
        // Given
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", "password123");

        // When
        Collection<? extends GrantedAuthority> authorities = userPrincipal.getAuthorities();

        // Then
        assertNotNull(authorities);
        assertTrue(authorities.isEmpty());
    }

    @Test
    void testAccountFlags() {
        // Given
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", "password123");

        // When & Then
        assertTrue(userPrincipal.isAccountNonExpired());
        assertTrue(userPrincipal.isAccountNonLocked());
        assertTrue(userPrincipal.isCredentialsNonExpired());
        assertTrue(userPrincipal.isEnabled());
    }
}