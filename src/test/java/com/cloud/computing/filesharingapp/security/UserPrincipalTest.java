package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
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
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);

        // When
        UserPrincipal userPrincipal = UserPrincipal.create(user);

        // Then
        assertEquals(1L, userPrincipal.getId());
        assertEquals("testuser", userPrincipal.getUsername());
        assertEquals("test@example.com", userPrincipal.getEmail());
        assertEquals("password123", userPrincipal.getPassword());
        assertTrue(userPrincipal.isEmailVerified());
        assertEquals(AccountStatus.ACTIVE, userPrincipal.getAccountStatus());
    }

    @Test
    void testConstructor() {
        // Given
        Long id = 1L;
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";
        boolean emailVerified = true;
        AccountStatus accountStatus = AccountStatus.ACTIVE;

        // When
        UserPrincipal userPrincipal = new UserPrincipal(id, username, email, password, emailVerified, accountStatus);

        // Then
        assertEquals(id, userPrincipal.getId());
        assertEquals(username, userPrincipal.getUsername());
        assertEquals(email, userPrincipal.getEmail());
        assertEquals(password, userPrincipal.getPassword());
        assertTrue(userPrincipal.isEmailVerified());
        assertEquals(AccountStatus.ACTIVE, userPrincipal.getAccountStatus());
    }

    @Test
    void testGetAuthorities() {
        // Given
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", "password123", true, AccountStatus.ACTIVE);

        // When
        Collection<? extends GrantedAuthority> authorities = userPrincipal.getAuthorities();

        // Then
        assertNotNull(authorities);
        assertTrue(authorities.isEmpty());
    }

    @Test
    void testAccountFlags() {
        // Given - verified and active user
        UserPrincipal verifiedUser = new UserPrincipal(1L, "testuser", "test@example.com", "password123", true, AccountStatus.ACTIVE);

        // When & Then
        assertTrue(verifiedUser.isAccountNonExpired());
        assertTrue(verifiedUser.isAccountNonLocked());
        assertTrue(verifiedUser.isCredentialsNonExpired());
        assertTrue(verifiedUser.isEnabled());
    }

    @Test
    void testUnverifiedUserIsDisabled() {
        // Given - unverified user
        UserPrincipal unverifiedUser = new UserPrincipal(1L, "testuser", "test@example.com", "password123", false, AccountStatus.PENDING);

        // When & Then
        assertTrue(unverifiedUser.isAccountNonExpired());
        assertTrue(unverifiedUser.isAccountNonLocked());
        assertTrue(unverifiedUser.isCredentialsNonExpired());
        assertFalse(unverifiedUser.isEnabled()); // Should be disabled
    }

    @Test
    void testSuspendedUserIsDisabled() {
        // Given - suspended user
        UserPrincipal suspendedUser = new UserPrincipal(1L, "testuser", "test@example.com", "password123", true, AccountStatus.SUSPENDED);

        // When & Then
        assertTrue(suspendedUser.isAccountNonExpired());
        assertTrue(suspendedUser.isAccountNonLocked());
        assertTrue(suspendedUser.isCredentialsNonExpired());
        assertFalse(suspendedUser.isEnabled()); // Should be disabled
    }
}