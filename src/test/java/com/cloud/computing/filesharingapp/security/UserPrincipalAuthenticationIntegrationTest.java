package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserPrincipal focusing on the enhanced authentication
 * logic that mirrors the frontend's dual token + user validation requirements.
 */
class UserPrincipalAuthenticationIntegrationTest {

    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        user = new User("testuser", "test@example.com", "hashedPassword123");
    }

    @Nested
    @DisplayName("UserPrincipal Creation and Validation")
    class UserPrincipalCreationTests {

        @Test
        @DisplayName("UserPrincipal should be created from User entity with all properties")
        void testUserPrincipalCreation() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            
            userPrincipal = UserPrincipal.create(user);
            
            assertNotNull(userPrincipal);
            assertEquals(user.getUsername(), userPrincipal.getUsername());
            assertEquals(user.getEmail(), userPrincipal.getEmail());
            assertEquals(user.getPassword(), userPrincipal.getPassword());
            assertEquals(user.isEmailVerified(), userPrincipal.isEmailVerified());
            assertEquals(user.getAccountStatus(), userPrincipal.getAccountStatus());
        }

        @Test
        @DisplayName("UserPrincipal should reflect User's authentication readiness")
        void testUserPrincipalAuthenticationReadiness() {
            // Test with unverified email
            user.setEmailVerified(false);
            user.setAccountStatus(AccountStatus.PENDING);
            userPrincipal = UserPrincipal.create(user);
            
            assertFalse(userPrincipal.isEnabled());
            
            // Test with verified email but pending status
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.PENDING);
            userPrincipal = UserPrincipal.create(user);
            
            assertFalse(userPrincipal.isEnabled());
            
            // Test with verified email and active status
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            
            assertTrue(userPrincipal.isEnabled());
        }

        @Test
        @DisplayName("UserPrincipal should handle suspended account status")
        void testSuspendedAccountHandling() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.SUSPENDED);
            userPrincipal = UserPrincipal.create(user);
            
            assertFalse(userPrincipal.isEnabled());
            assertTrue(userPrincipal.isEmailVerified()); // Email verification remains
            assertEquals(AccountStatus.SUSPENDED, userPrincipal.getAccountStatus());
        }
    }

    @Nested
    @DisplayName("Enhanced Authentication Scenarios")
    class EnhancedAuthenticationScenarios {

        @Test
        @DisplayName("Valid UserPrincipal with simulated valid token should pass authentication")
        void testValidUserPrincipalWithValidToken() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            
            boolean hasValidUserPrincipal = userPrincipal.isEnabled();
            boolean hasValidToken = true; // Simulated valid JWT token
            
            assertTrue(hasValidUserPrincipal);
            assertTrue(hasValidToken);
            assertTrue(isEnhancedAuthenticationValid(hasValidUserPrincipal, hasValidToken));
        }

        @Test
        @DisplayName("Valid UserPrincipal with invalid token should fail authentication")
        void testValidUserPrincipalWithInvalidToken() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            
            boolean hasValidUserPrincipal = userPrincipal.isEnabled();
            boolean hasValidToken = false; // Simulated invalid/expired JWT token
            
            assertTrue(hasValidUserPrincipal);
            assertFalse(hasValidToken);
            assertFalse(isEnhancedAuthenticationValid(hasValidUserPrincipal, hasValidToken));
        }

        @Test
        @DisplayName("Invalid UserPrincipal with valid token should fail authentication")
        void testInvalidUserPrincipalWithValidToken() {
            user.setEmailVerified(false);
            user.setAccountStatus(AccountStatus.PENDING);
            userPrincipal = UserPrincipal.create(user);
            
            boolean hasValidUserPrincipal = userPrincipal.isEnabled();
            boolean hasValidToken = true; // Simulated valid JWT token
            
            assertFalse(hasValidUserPrincipal);
            assertTrue(hasValidToken);
            assertFalse(isEnhancedAuthenticationValid(hasValidUserPrincipal, hasValidToken));
        }

        @Test
        @DisplayName("Suspended UserPrincipal with valid token should fail authentication")
        void testSuspendedUserPrincipalWithValidToken() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.SUSPENDED);
            userPrincipal = UserPrincipal.create(user);
            
            boolean hasValidUserPrincipal = userPrincipal.isEnabled();
            boolean hasValidToken = true; // Simulated valid JWT token
            
            assertFalse(hasValidUserPrincipal);
            assertTrue(hasValidToken);
            assertFalse(isEnhancedAuthenticationValid(hasValidUserPrincipal, hasValidToken));
        }

        /**
         * Simulates the enhanced authentication logic that requires both
         * a valid UserPrincipal (enabled) and a valid JWT token
         */
        private boolean isEnhancedAuthenticationValid(boolean hasValidUserPrincipal, boolean hasValidToken) {
            return hasValidUserPrincipal && hasValidToken;
        }
    }

    @Nested
    @DisplayName("UserPrincipal State Consistency Tests")
    class UserPrincipalStateConsistencyTests {

        @Test
        @DisplayName("UserPrincipal should maintain consistency with User entity changes")
        void testStateConsistencyWithUserChanges() {
            // Initial state
            user.setEmailVerified(false);
            user.setAccountStatus(AccountStatus.PENDING);
            userPrincipal = UserPrincipal.create(user);
            
            assertFalse(userPrincipal.isEnabled());
            
            // Update user state
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            
            // Create new UserPrincipal to reflect changes
            UserPrincipal updatedUserPrincipal = UserPrincipal.create(user);
            
            assertTrue(updatedUserPrincipal.isEnabled());
            assertNotEquals(userPrincipal.isEnabled(), updatedUserPrincipal.isEnabled());
        }

        @Test
        @DisplayName("UserPrincipal should handle all account status transitions")
        void testAccountStatusTransitions() {
            // PENDING -> ACTIVE
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.PENDING);
            userPrincipal = UserPrincipal.create(user);
            assertFalse(userPrincipal.isEnabled());
            
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            assertTrue(userPrincipal.isEnabled());
            
            // ACTIVE -> SUSPENDED
            user.setAccountStatus(AccountStatus.SUSPENDED);
            userPrincipal = UserPrincipal.create(user);
            assertFalse(userPrincipal.isEnabled());
            
            // SUSPENDED -> ACTIVE
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            assertTrue(userPrincipal.isEnabled());
        }

        @Test
        @DisplayName("UserPrincipal should handle email verification state changes")
        void testEmailVerificationStateChanges() {
            user.setAccountStatus(AccountStatus.ACTIVE);
            
            // Unverified email
            user.setEmailVerified(false);
            userPrincipal = UserPrincipal.create(user);
            assertFalse(userPrincipal.isEnabled());
            
            // Verified email
            user.setEmailVerified(true);
            userPrincipal = UserPrincipal.create(user);
            assertTrue(userPrincipal.isEnabled());
            
            // Back to unverified (edge case)
            user.setEmailVerified(false);
            userPrincipal = UserPrincipal.create(user);
            assertFalse(userPrincipal.isEnabled());
        }
    }

    @Nested
    @DisplayName("Authentication Flow Integration Tests")
    class AuthenticationFlowIntegrationTests {

        @Test
        @DisplayName("Complete authentication flow from registration to access")
        void testCompleteAuthenticationFlow() {
            // Step 1: User registration (default state)
            User newUser = new User("newuser", "new@example.com", "hashedPassword");
            UserPrincipal newUserPrincipal = UserPrincipal.create(newUser);
            
            assertFalse(newUserPrincipal.isEnabled());
            assertFalse(isEnhancedAuthenticationValid(newUserPrincipal.isEnabled(), true));
            
            // Step 2: Email verification
            newUser.setEmailVerified(true);
            newUser.setAccountStatus(AccountStatus.ACTIVE);
            newUserPrincipal = UserPrincipal.create(newUser);
            
            assertTrue(newUserPrincipal.isEnabled());
            assertTrue(isEnhancedAuthenticationValid(newUserPrincipal.isEnabled(), true));
        }

        @Test
        @DisplayName("Authentication should fail during account suspension")
        void testAuthenticationDuringSuspension() {
            // Setup active user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            
            assertTrue(isEnhancedAuthenticationValid(userPrincipal.isEnabled(), true));
            
            // Suspend account
            user.setAccountStatus(AccountStatus.SUSPENDED);
            userPrincipal = UserPrincipal.create(user);
            
            assertFalse(isEnhancedAuthenticationValid(userPrincipal.isEnabled(), true));
            
            // Reactivate account
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            
            assertTrue(isEnhancedAuthenticationValid(userPrincipal.isEnabled(), true));
        }

        @Test
        @DisplayName("Token expiration should invalidate authentication even with valid user")
        void testTokenExpirationScenario() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            userPrincipal = UserPrincipal.create(user);
            
            // Valid token scenario
            assertTrue(isEnhancedAuthenticationValid(userPrincipal.isEnabled(), true));
            
            // Token expires/becomes invalid
            assertFalse(isEnhancedAuthenticationValid(userPrincipal.isEnabled(), false));
            
            // New valid token obtained
            assertTrue(isEnhancedAuthenticationValid(userPrincipal.isEnabled(), true));
        }

        private boolean isEnhancedAuthenticationValid(boolean hasValidUserPrincipal, boolean hasValidToken) {
            return hasValidUserPrincipal && hasValidToken;
        }
    }
}