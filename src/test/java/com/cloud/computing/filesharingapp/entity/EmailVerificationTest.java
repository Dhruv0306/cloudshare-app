package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

class EmailVerificationTest {

    private EmailVerification emailVerification;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setId(1L);
        emailVerification = new EmailVerification();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor should initialize timestamps correctly")
        void testDefaultConstructor() {
            EmailVerification verification = new EmailVerification();

            assertNotNull(verification);
            assertNotNull(verification.getCreatedAt());
            assertNotNull(verification.getExpiresAt());
            assertFalse(verification.isUsed());

            // Verify that createdAt is set to current time (within 1 second tolerance)
            assertTrue(verification.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
            assertTrue(verification.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(1)));

            // Verify that expiresAt is 15 minutes after createdAt
            LocalDateTime expectedExpiry = verification.getCreatedAt().plusMinutes(15);
            assertEquals(expectedExpiry, verification.getExpiresAt());
        }

        @Test
        @DisplayName("Parameterized constructor should set all fields correctly")
        void testParameterizedConstructor() {
            String email = "test@example.com";
            String code = "123456";

            EmailVerification verification = new EmailVerification(email, code, testUser);

            assertEquals(email, verification.getEmail());
            assertEquals(code, verification.getVerificationCode());
            assertEquals(testUser, verification.getUser());
            assertNotNull(verification.getCreatedAt());
            assertNotNull(verification.getExpiresAt());
            assertFalse(verification.isUsed());

            // Verify timestamps are set correctly
            assertTrue(verification.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
            LocalDateTime expectedExpiry = verification.getCreatedAt().plusMinutes(15);
            assertEquals(expectedExpiry, verification.getExpiresAt());
        }

        @Test
        @DisplayName("Parameterized constructor should handle null user")
        void testParameterizedConstructorWithNullUser() {
            String email = "test@example.com";
            String code = "123456";

            EmailVerification verification = new EmailVerification(email, code, null);

            assertEquals(email, verification.getEmail());
            assertEquals(code, verification.getVerificationCode());
            assertNull(verification.getUser());
            assertNotNull(verification.getCreatedAt());
            assertNotNull(verification.getExpiresAt());
        }

        @Test
        @DisplayName("Default constructor should ensure consistent timestamp calculation")
        void testDefaultConstructorTimestampConsistency() {
            EmailVerification verification = new EmailVerification();

            // The key improvement: expiresAt should be calculated from createdAt, not from
            // a new LocalDateTime.now()
            // This ensures no timing discrepancy between the two timestamps
            LocalDateTime createdAt = verification.getCreatedAt();
            LocalDateTime expiresAt = verification.getExpiresAt();

            // Verify exact 15-minute difference
            assertEquals(createdAt.plusMinutes(15), expiresAt);

            // Verify that the difference is exactly 15 minutes (900 seconds)
            long secondsDifference = java.time.Duration.between(createdAt, expiresAt).getSeconds();
            assertEquals(900, secondsDifference);
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("ID getter and setter should work correctly")
        void testIdGetterSetter() {
            Long id = 1L;
            emailVerification.setId(id);
            assertEquals(id, emailVerification.getId());
        }

        @Test
        @DisplayName("Email getter and setter should work correctly")
        void testEmailGetterSetter() {
            String email = "test@example.com";
            emailVerification.setEmail(email);
            assertEquals(email, emailVerification.getEmail());
        }

        @Test
        @DisplayName("Verification code getter and setter should work correctly")
        void testVerificationCodeGetterSetter() {
            String code = "123456";
            emailVerification.setVerificationCode(code);
            assertEquals(code, emailVerification.getVerificationCode());
        }

        @Test
        @DisplayName("CreatedAt getter and setter should work correctly")
        void testCreatedAtGetterSetter() {
            LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
            emailVerification.setCreatedAt(createdAt);
            assertEquals(createdAt, emailVerification.getCreatedAt());
        }

        @Test
        @DisplayName("ExpiresAt getter and setter should work correctly")
        void testExpiresAtGetterSetter() {
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
            emailVerification.setExpiresAt(expiresAt);
            assertEquals(expiresAt, emailVerification.getExpiresAt());
        }

        @Test
        @DisplayName("Used getter and setter should work correctly")
        void testUsedGetterSetter() {
            assertFalse(emailVerification.isUsed()); // Default value

            emailVerification.setUsed(true);
            assertTrue(emailVerification.isUsed());

            emailVerification.setUsed(false);
            assertFalse(emailVerification.isUsed());
        }

        @Test
        @DisplayName("User getter and setter should work correctly")
        void testUserGetterSetter() {
            emailVerification.setUser(testUser);
            assertEquals(testUser, emailVerification.getUser());

            emailVerification.setUser(null);
            assertNull(emailVerification.getUser());
        }
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        @Test
        @DisplayName("isExpired should return false for non-expired verification")
        void testIsExpiredReturnsFalseForNonExpired() {
            // Set expiry to 1 hour in the future
            emailVerification.setExpiresAt(LocalDateTime.now().plusHours(1));

            assertFalse(emailVerification.isExpired());
        }

        @Test
        @DisplayName("isExpired should return true for expired verification")
        void testIsExpiredReturnsTrueForExpired() {
            // Set expiry to 1 hour in the past
            emailVerification.setExpiresAt(LocalDateTime.now().minusHours(1));

            assertTrue(emailVerification.isExpired());
        }

        @Test
        @DisplayName("isExpired should return true for verification expiring exactly now")
        void testIsExpiredReturnsTrueForExactlyNow() {
            // Set expiry to current time (should be considered expired)
            emailVerification.setExpiresAt(LocalDateTime.now().minusNanos(1));

            assertTrue(emailVerification.isExpired());
        }

        @Test
        @DisplayName("isExpired should handle edge case of very recent expiry")
        void testIsExpiredHandlesRecentExpiry() {
            // Set expiry to 1 second ago
            emailVerification.setExpiresAt(LocalDateTime.now().minusSeconds(1));

            assertTrue(emailVerification.isExpired());
        }

        @Test
        @DisplayName("isExpired should handle edge case of very near future expiry")
        void testIsExpiredHandlesNearFutureExpiry() {
            // Set expiry to 1 second in the future
            emailVerification.setExpiresAt(LocalDateTime.now().plusSeconds(1));

            assertFalse(emailVerification.isExpired());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Complete verification lifecycle should work correctly")
        void testCompleteVerificationLifecycle() {
            String email = "user@example.com";
            String code = "987654";

            // Create verification
            EmailVerification verification = new EmailVerification(email, code, testUser);

            // Verify initial state
            assertFalse(verification.isUsed());
            assertFalse(verification.isExpired());
            assertEquals(email, verification.getEmail());
            assertEquals(code, verification.getVerificationCode());
            assertEquals(testUser, verification.getUser());

            // Mark as used
            verification.setUsed(true);
            assertTrue(verification.isUsed());

            // Should still not be expired if within 15 minutes
            assertFalse(verification.isExpired());
        }

        @Test
        @DisplayName("Verification should expire after set time")
        void testVerificationExpiresAfterSetTime() {
            EmailVerification verification = new EmailVerification("test@example.com", "123456", testUser);

            // Manually set expiry to past
            verification.setExpiresAt(LocalDateTime.now().minusMinutes(1));

            assertTrue(verification.isExpired());
            // Used status should be independent of expiry
            assertFalse(verification.isUsed());
        }

        @Test
        @DisplayName("Multiple verifications for same user should be independent")
        void testMultipleVerificationsIndependence() {
            EmailVerification verification1 = new EmailVerification("test@example.com", "111111", testUser);
            EmailVerification verification2 = new EmailVerification("test@example.com", "222222", testUser);

            // Mark first as used
            verification1.setUsed(true);

            // Second should remain unused
            assertTrue(verification1.isUsed());
            assertFalse(verification2.isUsed());

            // Both should have different codes
            assertNotEquals(verification1.getVerificationCode(), verification2.getVerificationCode());

            // Both should reference same user
            assertEquals(verification1.getUser(), verification2.getUser());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Conditions")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null email gracefully")
        void testNullEmail() {
            emailVerification.setEmail(null);
            assertNull(emailVerification.getEmail());
        }

        @Test
        @DisplayName("Should handle null verification code gracefully")
        void testNullVerificationCode() {
            emailVerification.setVerificationCode(null);
            assertNull(emailVerification.getVerificationCode());
        }

        @Test
        @DisplayName("Should handle null timestamps gracefully")
        void testNullTimestamps() {
            emailVerification.setCreatedAt(null);
            emailVerification.setExpiresAt(null);

            assertNull(emailVerification.getCreatedAt());
            assertNull(emailVerification.getExpiresAt());
        }

        @Test
        @DisplayName("isExpired should handle null expiresAt")
        void testIsExpiredWithNullExpiresAt() {
            emailVerification.setExpiresAt(null);

            // This should throw NullPointerException as per current implementation
            assertThrows(NullPointerException.class, () -> {
                emailVerification.isExpired();
            });
        }

        @Test
        @DisplayName("Should handle empty string values")
        void testEmptyStringValues() {
            emailVerification.setEmail("");
            emailVerification.setVerificationCode("");

            assertEquals("", emailVerification.getEmail());
            assertEquals("", emailVerification.getVerificationCode());
        }

        @Test
        @DisplayName("Should handle very long verification codes")
        void testLongVerificationCode() {
            String longCode = "1234567890"; // Longer than expected 6 characters
            emailVerification.setVerificationCode(longCode);
            assertEquals(longCode, emailVerification.getVerificationCode());
        }

        @Test
        @DisplayName("Should handle special characters in verification code")
        void testSpecialCharactersInCode() {
            String specialCode = "!@#$%^";
            emailVerification.setVerificationCode(specialCode);
            assertEquals(specialCode, emailVerification.getVerificationCode());
        }
    }

    @Nested
    @DisplayName("Validation Constraint Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should accept valid email format")
        void testValidEmailFormat() {
            String validEmail = "user@example.com";
            emailVerification.setEmail(validEmail);
            assertEquals(validEmail, emailVerification.getEmail());
        }

        @Test
        @DisplayName("Should accept 6-character verification code")
        void testValidVerificationCodeLength() {
            String validCode = "123456";
            emailVerification.setVerificationCode(validCode);
            assertEquals(validCode, emailVerification.getVerificationCode());
        }

        @Test
        @DisplayName("Should handle maximum email length")
        void testMaximumEmailLength() {
            // Create email with exactly 100 characters (including domain)
            String longEmail = "a".repeat(88) + "@example.com"; // 88 + 12 = 100 chars
            emailVerification.setEmail(longEmail);
            assertEquals(longEmail, emailVerification.getEmail());
        }
    }
}