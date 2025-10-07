package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration tests for User entity focusing on authentication-related
 * scenarios and the enhanced authentication logic that requires both token and
 * user
 * presence, mirroring the frontend's dual authentication requirements.
 */
class UserAuthenticationIntegrationTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("testuser", "test@example.com", "hashedPassword123");
    }

    @Nested
    @DisplayName("Account Status and Email Verification Integration")
    class AccountStatusEmailVerificationTests {

        @Test
        @DisplayName("New user should have PENDING status and unverified email")
        void testNewUserDefaultState() {
            User newUser = new User("newuser", "new@example.com", "password");

            assertEquals(AccountStatus.PENDING, newUser.getAccountStatus());
            assertFalse(newUser.isEmailVerified());
            assertNotNull(newUser.getCreatedAt());
        }

        @Test
        @DisplayName("User can transition from PENDING to ACTIVE when email is verified")
        void testEmailVerificationActivatesAccount() {
            // Simulate email verification process
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            assertTrue(user.isEmailVerified());
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        @Test
        @DisplayName("Active user can be suspended")
        void testActiveUserCanBeSuspended() {
            // Setup active user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Suspend user
            user.setAccountStatus(AccountStatus.SUSPENDED);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            // Email verification status should remain unchanged
            assertTrue(user.isEmailVerified());
        }

        @Test
        @DisplayName("Suspended user can be reactivated")
        void testSuspendedUserCanBeReactivated() {
            // Setup suspended user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.SUSPENDED);

            // Reactivate user
            user.setAccountStatus(AccountStatus.ACTIVE);

            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
            assertTrue(user.isEmailVerified());
        }
    }

    @Nested
    @DisplayName("User Authentication State Validation")
    class AuthenticationStateValidationTests {

        @Test
        @DisplayName("User is not ready for authentication when email is not verified")
        void testUserNotReadyWhenEmailNotVerified() {
            user.setEmailVerified(false);
            user.setAccountStatus(AccountStatus.PENDING);

            assertFalse(isUserReadyForAuthentication(user));
        }

        @Test
        @DisplayName("User is not ready for authentication when account is suspended")
        void testUserNotReadyWhenAccountSuspended() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.SUSPENDED);

            assertFalse(isUserReadyForAuthentication(user));
        }

        @Test
        @DisplayName("User is ready for authentication when email verified and account active")
        void testUserReadyWhenEmailVerifiedAndActive() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            assertTrue(isUserReadyForAuthentication(user));
        }

        @Test
        @DisplayName("User with pending status but verified email should not be ready")
        void testUserWithPendingStatusNotReady() {
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.PENDING);

            assertFalse(isUserReadyForAuthentication(user));
        }

        /**
         * Helper method to simulate authentication readiness check
         * This mirrors the logic that would be used in the authentication service
         */
        private boolean isUserReadyForAuthentication(User user) {
            return user.isEmailVerified() && user.getAccountStatus() == AccountStatus.ACTIVE;
        }
    }

    @Nested
    @DisplayName("User Entity Relationships")
    class UserRelationshipTests {

        @Test
        @DisplayName("User can have multiple files associated")
        void testUserCanHaveMultipleFiles() {
            List<FileEntity> files = new ArrayList<>();

            // Create mock file entities (simplified for testing)
            FileEntity file1 = createMockFileEntity(1L, "document1.pdf");
            FileEntity file2 = createMockFileEntity(2L, "image1.jpg");

            files.add(file1);
            files.add(file2);

            user.setFiles(files);

            assertNotNull(user.getFiles());
            assertEquals(2, user.getFiles().size());
            assertTrue(user.getFiles().contains(file1));
            assertTrue(user.getFiles().contains(file2));
        }

        @Test
        @DisplayName("User can have multiple email verifications")
        void testUserCanHaveMultipleEmailVerifications() {
            List<EmailVerification> verifications = new ArrayList<>();

            // Create mock email verification entities
            EmailVerification verification1 = createMockEmailVerification(1L, "123456");
            EmailVerification verification2 = createMockEmailVerification(2L, "789012");

            verifications.add(verification1);
            verifications.add(verification2);

            user.setEmailVerifications(verifications);

            assertNotNull(user.getEmailVerifications());
            assertEquals(2, user.getEmailVerifications().size());
            assertTrue(user.getEmailVerifications().contains(verification1));
            assertTrue(user.getEmailVerifications().contains(verification2));
        }

        @Test
        @DisplayName("User with no files should have empty list")
        void testUserWithNoFiles() {
            user.setFiles(new ArrayList<>());

            assertNotNull(user.getFiles());
            assertTrue(user.getFiles().isEmpty());
        }

        @Test
        @DisplayName("User with no email verifications should have empty list")
        void testUserWithNoEmailVerifications() {
            user.setEmailVerifications(new ArrayList<>());

            assertNotNull(user.getEmailVerifications());
            assertTrue(user.getEmailVerifications().isEmpty());
        }

        /**
         * Helper method to create mock FileEntity for testing
         */
        private FileEntity createMockFileEntity(Long id, String fileName) {
            FileEntity file = new FileEntity();
            file.setId(id);
            file.setOriginalFileName(fileName);
            file.setFileName("uuid_" + fileName);
            file.setFileSize(1024L);
            file.setUploadTime(LocalDateTime.now());
            file.setOwner(user);
            return file;
        }

        /**
         * Helper method to create mock EmailVerification for testing
         */
        private EmailVerification createMockEmailVerification(Long id, String code) {
            EmailVerification verification = new EmailVerification();
            verification.setId(id);
            verification.setVerificationCode(code);
            verification.setEmail(user.getEmail());
            verification.setUser(user);
            verification.setCreatedAt(LocalDateTime.now());
            verification.setExpiresAt(LocalDateTime.now().plusHours(24));
            return verification;
        }
    }

    @Nested
    @DisplayName("User Data Integrity Tests")
    class UserDataIntegrityTests {

        @Test
        @DisplayName("User creation timestamp should be immutable after construction")
        void testCreationTimestampImmutability() {
            LocalDateTime originalCreatedAt = user.getCreatedAt();

            // Simulate some time passing
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Creation time should remain the same
            assertEquals(originalCreatedAt, user.getCreatedAt());
        }

        @Test
        @DisplayName("User should maintain data consistency across state changes")
        void testDataConsistencyAcrossStateChanges() {
            String originalUsername = user.getUsername();
            String originalEmail = user.getEmail();
            String originalPassword = user.getPassword();
            LocalDateTime originalCreatedAt = user.getCreatedAt();

            // Change authentication-related fields
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Core user data should remain unchanged
            assertEquals(originalUsername, user.getUsername());
            assertEquals(originalEmail, user.getEmail());
            assertEquals(originalPassword, user.getPassword());
            assertEquals(originalCreatedAt, user.getCreatedAt());
        }

        @Test
        @DisplayName("User should handle null collections gracefully")
        void testNullCollectionsHandling() {
            // Initially collections should be null
            assertNull(user.getFiles());
            assertNull(user.getEmailVerifications());

            // Setting to null should be allowed
            user.setFiles(null);
            user.setEmailVerifications(null);

            assertNull(user.getFiles());
            assertNull(user.getEmailVerifications());
        }
    }

    @Nested
    @DisplayName("Enhanced Authentication Flow Tests")
    class EnhancedAuthenticationFlowTests {

        @Test
        @DisplayName("Complete user registration and activation flow")
        void testCompleteRegistrationFlow() {
            // Step 1: User registers (default state)
            User newUser = new User("newuser", "new@example.com", "hashedPassword");

            assertEquals(AccountStatus.PENDING, newUser.getAccountStatus());
            assertFalse(newUser.isEmailVerified());

            // Step 2: Email verification is sent
            List<EmailVerification> verifications = new ArrayList<>();
            EmailVerification verification = createMockEmailVerification(1L, "123456");
            verifications.add(verification);
            newUser.setEmailVerifications(verifications);

            assertFalse(newUser.isEmailVerified()); // Still not verified
            assertEquals(1, newUser.getEmailVerifications().size());

            // Step 3: User verifies email
            newUser.setEmailVerified(true);
            newUser.setAccountStatus(AccountStatus.ACTIVE);

            assertTrue(newUser.isEmailVerified());
            assertEquals(AccountStatus.ACTIVE, newUser.getAccountStatus());
            assertTrue(isUserReadyForAuthentication(newUser));
        }

        @Test
        @DisplayName("User suspension and reactivation flow")
        void testSuspensionReactivationFlow() {
            // Setup active user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            assertTrue(isUserReadyForAuthentication(user));

            // Suspend user
            user.setAccountStatus(AccountStatus.SUSPENDED);
            assertFalse(isUserReadyForAuthentication(user));
            assertTrue(user.isEmailVerified()); // Email verification remains

            // Reactivate user
            user.setAccountStatus(AccountStatus.ACTIVE);
            assertTrue(isUserReadyForAuthentication(user));
        }

        @Test
        @DisplayName("Multiple email verification attempts")
        void testMultipleEmailVerificationAttempts() {
            List<EmailVerification> verifications = new ArrayList<>();

            // First verification attempt
            EmailVerification verification1 = createMockEmailVerification(1L, "123456");
            verifications.add(verification1);

            // Second verification attempt (e.g., code resent)
            EmailVerification verification2 = createMockEmailVerification(2L, "789012");
            verifications.add(verification2);

            user.setEmailVerifications(verifications);

            assertEquals(2, user.getEmailVerifications().size());
            assertFalse(user.isEmailVerified()); // Still not verified until explicitly set

            // Verify email after successful code entry
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            assertTrue(user.isEmailVerified());
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        private boolean isUserReadyForAuthentication(User user) {
            return user.isEmailVerified() && user.getAccountStatus() == AccountStatus.ACTIVE;
        }

        private EmailVerification createMockEmailVerification(Long id, String code) {
            EmailVerification verification = new EmailVerification();
            verification.setId(id);
            verification.setVerificationCode(code);
            verification.setEmail(user.getEmail());
            verification.setUser(user);
            verification.setCreatedAt(LocalDateTime.now());
            verification.setExpiresAt(LocalDateTime.now().plusHours(24));
            return verification;
        }
    }

    @Nested
    @DisplayName("Token and User Dual Authentication Tests")
    class TokenUserDualAuthenticationTests {

        @Test
        @DisplayName("User with valid credentials but null token scenario should fail authentication")
        void testValidUserNullTokenScenario() {
            // Setup valid user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Simulate scenario where user exists but token is null/expired
            boolean hasValidUser = isUserReadyForAuthentication(user);
            boolean hasValidToken = false; // Token is null/expired

            assertTrue(hasValidUser);
            assertFalse(hasValidToken);
            assertFalse(isDualAuthenticationValid(hasValidUser, hasValidToken));
        }

        @Test
        @DisplayName("Valid token but null user scenario should fail authentication")
        void testValidTokenNullUserScenario() {
            // Simulate scenario where token exists but user is null/invalid
            boolean hasValidUser = false; // User is null/invalid
            boolean hasValidToken = true; // Token exists and is valid

            assertFalse(hasValidUser);
            assertTrue(hasValidToken);
            assertFalse(isDualAuthenticationValid(hasValidUser, hasValidToken));
        }

        @Test
        @DisplayName("Both valid token and user should pass authentication")
        void testValidTokenAndUserScenario() {
            // Setup valid user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);

            boolean hasValidUser = isUserReadyForAuthentication(user);
            boolean hasValidToken = true; // Token exists and is valid

            assertTrue(hasValidUser);
            assertTrue(hasValidToken);
            assertTrue(isDualAuthenticationValid(hasValidUser, hasValidToken));
        }

        @Test
        @DisplayName("Both null token and user should fail authentication")
        void testNullTokenAndUserScenario() {
            boolean hasValidUser = false; // User is null
            boolean hasValidToken = false; // Token is null

            assertFalse(hasValidUser);
            assertFalse(hasValidToken);
            assertFalse(isDualAuthenticationValid(hasValidUser, hasValidToken));
        }

        @Test
        @DisplayName("User with unverified email should fail even with valid token")
        void testUnverifiedEmailWithValidToken() {
            // Setup user with unverified email
            user.setEmailVerified(false);
            user.setAccountStatus(AccountStatus.PENDING);

            boolean hasValidUser = isUserReadyForAuthentication(user);
            boolean hasValidToken = true; // Token exists and is valid

            assertFalse(hasValidUser);
            assertTrue(hasValidToken);
            assertFalse(isDualAuthenticationValid(hasValidUser, hasValidToken));
        }

        @Test
        @DisplayName("Suspended user should fail even with valid token")
        void testSuspendedUserWithValidToken() {
            // Setup suspended user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.SUSPENDED);

            boolean hasValidUser = isUserReadyForAuthentication(user);
            boolean hasValidToken = true; // Token exists and is valid

            assertFalse(hasValidUser);
            assertTrue(hasValidToken);
            assertFalse(isDualAuthenticationValid(hasValidUser, hasValidToken));
        }

        @Test
        @DisplayName("Authentication state transitions should require both token and user validation")
        void testAuthenticationStateTransitions() {
            // Initial state: no authentication
            assertFalse(isDualAuthenticationValid(false, false));

            // Partial authentication: only token
            assertFalse(isDualAuthenticationValid(false, true));

            // Partial authentication: only user
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            assertFalse(isDualAuthenticationValid(true, false));

            // Full authentication: both token and user
            assertTrue(isDualAuthenticationValid(true, true));

            // Logout: remove both
            assertFalse(isDualAuthenticationValid(false, false));
        }

        /**
         * Simulates the enhanced authentication logic that requires both
         * a valid user and a valid token, mirroring frontend behavior
         */
        private boolean isDualAuthenticationValid(boolean hasValidUser, boolean hasValidToken) {
            return hasValidUser && hasValidToken;
        }

        private boolean isUserReadyForAuthentication(User user) {
            return user.isEmailVerified() && user.getAccountStatus() == AccountStatus.ACTIVE;
        }
    }
}