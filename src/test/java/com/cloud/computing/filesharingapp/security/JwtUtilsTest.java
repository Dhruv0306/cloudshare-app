package com.cloud.computing.filesharingapp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    @InjectMocks
    private JwtUtils jwtUtils;

    @Mock
    private Authentication authentication;

    @Mock
    private UserPrincipal userPrincipal;

    private final String testSecret = "dGVzdFNlY3JldEtleTEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MA==";
    private final int testExpiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", testExpiration);
    }

    @Test
    void testGenerateJwtToken() {
        // Given
        String username = "testuser";
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userPrincipal.getUsername()).thenReturn(username);

        // When
        String token = jwtUtils.generateJwtToken(authentication);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts separated by dots
    }

    @Test
    void testGetUserNameFromJwtToken() {
        // Given
        String username = "testuser";
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userPrincipal.getUsername()).thenReturn(username);
        
        String token = jwtUtils.generateJwtToken(authentication);

        // When
        String extractedUsername = jwtUtils.getUserNameFromJwtToken(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    void testValidateJwtToken() {
        // Given
        String username = "testuser";
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userPrincipal.getUsername()).thenReturn(username);
        
        String token = jwtUtils.generateJwtToken(authentication);

        // When
        boolean isValid = jwtUtils.validateJwtToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void testValidateInvalidJwtToken() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When
        boolean isValid = jwtUtils.validateJwtToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateEmptyJwtToken() {
        // Given
        String emptyToken = "";

        // When
        boolean isValid = jwtUtils.validateJwtToken(emptyToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateNullJwtToken() {
        // Given
        String nullToken = null;

        // When
        boolean isValid = jwtUtils.validateJwtToken(nullToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testValidateMalformedJwtToken() {
        // Given
        String malformedToken = "malformed-token-without-proper-structure";

        // When
        boolean isValid = jwtUtils.validateJwtToken(malformedToken);

        // Then
        assertFalse(isValid);
    }
}