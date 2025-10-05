package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.EmailVerification;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.entity.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EmailVerificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;
    
    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private EmailVerification testVerification;

    @BeforeEach
    void setUp() {
        // Create and persist test user
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setAccountStatus(AccountStatus.PENDING);
        testUser = entityManager.persistAndFlush(testUser);

        // Create test verification
        testVerification = new EmailVerification("test@example.com", "123456", testUser);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Should save and retrieve email verification")
        void testSaveAndRetrieve() {
            // Save verification
            EmailVerification saved = emailVerificationRepository.save(testVerification);
            entityManager.flush();

            assertNotNull(saved.getId());
            assertEquals(testVerification.getEmail(), saved.getEmail());
            assertEquals(testVerification.getVerificationCode(), saved.getVerificationCode());
            assertEquals(testVerification.getUser().getId(), saved.getUser().getId());

            // Retrieve verification
            Optional<EmailVerification> retrieved = emailVerificationRepository.findById(saved.getId());
            assertTrue(retrieved.isPresent());
            assertEquals(saved.getEmail(), retrieved.get().getEmail());
        }

        @Test
        @DisplayName("Should delete email verification")
        void testDelete() {
            EmailVerification saved = emailVerificationRepository.save(testVerification);
            entityManager.flush();

            Long id = saved.getId();
            emailVerificationRepository.delete(saved);
            entityManager.flush();

            Optional<EmailVerification> retrieved = emailVerificationRepository.findById(id);
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("Should update email verification")
        void testUpdate() {
            EmailVerification saved = emailVerificationRepository.save(testVerification);
            entityManager.flush();

            // Update verification
            saved.setUsed(true);
            saved.setVerificationCode("654321");
            EmailVerification updated = emailVerificationRepository.save(saved);
            entityManager.flush();

            assertEquals(saved.getId(), updated.getId());
            assertTrue(updated.isUsed());
            assertEquals("654321", updated.getVerificationCode());
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodTests {

        @Test
        @DisplayName("Should find verification by email ordered by creation date")
        void testFindByEmailOrderByCreatedAtDesc() {
            // Save first verification
            emailVerificationRepository.save(testVerification);
            
            // Create and save second verification for same email
            EmailVerification secondVerification = new EmailVerification("test@example.com", "654321", testUser);
            secondVerification.setCreatedAt(LocalDateTime.now().plusMinutes(1));
            emailVerificationRepository.save(secondVerification);
            entityManager.flush();

            List<EmailVerification> verifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("test@example.com");
            
            assertEquals(2, verifications.size());
            // Should be ordered by creation date descending (newest first)
            assertEquals("654321", verifications.get(0).getVerificationCode());
            assertEquals("123456", verifications.get(1).getVerificationCode());
        }

        @Test
        @DisplayName("Should find most recent unused verification by email")
        void testFindTopByEmailAndUsedFalseOrderByCreatedAtDesc() {
            // Save first verification
            emailVerificationRepository.save(testVerification);
            
            // Create and save more recent verification
            EmailVerification recentVerification = new EmailVerification("test@example.com", "999999", testUser);
            recentVerification.setCreatedAt(LocalDateTime.now().plusMinutes(1));
            emailVerificationRepository.save(recentVerification);
            entityManager.flush();

            Optional<EmailVerification> verification = emailVerificationRepository
                    .findTopByEmailAndUsedFalseOrderByCreatedAtDesc("test@example.com");
            
            assertTrue(verification.isPresent());
            assertEquals("999999", verification.get().getVerificationCode());
        }

        @Test
        @DisplayName("Should find verification by email and code when unused")
        void testFindByEmailAndVerificationCodeAndUsedFalse() {
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            Optional<EmailVerification> verification = emailVerificationRepository
                    .findByEmailAndVerificationCodeAndUsedFalse("test@example.com", "123456");
            
            assertTrue(verification.isPresent());
            assertEquals("test@example.com", verification.get().getEmail());
            assertEquals("123456", verification.get().getVerificationCode());
            assertFalse(verification.get().isUsed());
        }

        @Test
        @DisplayName("Should not find used verification by email and code")
        void testFindByEmailAndVerificationCodeAndUsedFalse_WhenUsed() {
            testVerification.setUsed(true);
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            Optional<EmailVerification> verification = emailVerificationRepository
                    .findByEmailAndVerificationCodeAndUsedFalse("test@example.com", "123456");
            
            assertFalse(verification.isPresent());
        }

        @Test
        @DisplayName("Should find expired unused verifications")
        void testFindByExpiresAtBeforeAndUsedFalse() {
            // Create expired unused verification
            EmailVerification expiredVerification = new EmailVerification("expired@example.com", "111111", testUser);
            expiredVerification.setExpiresAt(LocalDateTime.now().minusHours(1));
            emailVerificationRepository.save(expiredVerification);
            
            // Create expired used verification (should not be found)
            EmailVerification expiredUsedVerification = new EmailVerification("expired-used@example.com", "222222", testUser);
            expiredUsedVerification.setExpiresAt(LocalDateTime.now().minusHours(1));
            expiredUsedVerification.setUsed(true);
            emailVerificationRepository.save(expiredUsedVerification);
            
            // Save non-expired verification
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            List<EmailVerification> expiredVerifications = emailVerificationRepository
                    .findByExpiresAtBeforeAndUsedFalse(LocalDateTime.now());
            
            assertEquals(1, expiredVerifications.size());
            assertEquals("expired@example.com", expiredVerifications.get(0).getEmail());
            assertFalse(expiredVerifications.get(0).isUsed());
        }

        @Test
        @DisplayName("Should count unused verifications for email after specific time")
        void testCountByEmailAndCreatedAtAfterAndUsedFalse() {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            
            // Create old verification (should not be counted)
            EmailVerification oldVerification = new EmailVerification("test@example.com", "111111", testUser);
            oldVerification.setCreatedAt(oneHourAgo.minusMinutes(30));
            emailVerificationRepository.save(oldVerification);
            
            // Create recent verification (should be counted)
            testVerification.setCreatedAt(oneHourAgo.plusMinutes(30));
            emailVerificationRepository.save(testVerification);
            
            // Create recent used verification (should not be counted)
            EmailVerification recentUsedVerification = new EmailVerification("test@example.com", "333333", testUser);
            recentUsedVerification.setCreatedAt(oneHourAgo.plusMinutes(45));
            recentUsedVerification.setUsed(true);
            emailVerificationRepository.save(recentUsedVerification);
            
            entityManager.flush();

            long count = emailVerificationRepository
                    .countByEmailAndCreatedAtAfterAndUsedFalse("test@example.com", oneHourAgo);
            
            assertEquals(1, count);
        }
    }

    @Nested
    @DisplayName("Relationship Tests")
    class RelationshipTests {

        @Test
        @DisplayName("Should maintain user relationship")
        void testUserRelationship() {
            EmailVerification saved = emailVerificationRepository.save(testVerification);
            entityManager.flush();
            entityManager.clear(); // Clear persistence context

            EmailVerification retrieved = emailVerificationRepository.findById(saved.getId()).orElse(null);
            assertNotNull(retrieved);
            assertNotNull(retrieved.getUser());
            assertEquals(testUser.getId(), retrieved.getUser().getId());
            assertEquals(testUser.getEmail(), retrieved.getUser().getEmail());
        }

        @Test
        @DisplayName("Should handle user-verification relationship")
        void testUserVerificationRelationship() {
            // Create and persist a new user for this test using repository
            User relationshipTestUser = new User("relationshipuser", "relationship@example.com", "password123");
            relationshipTestUser.setAccountStatus(AccountStatus.PENDING);
            relationshipTestUser = userRepository.saveAndFlush(relationshipTestUser);
            
            // Create verification with the persisted user
            EmailVerification verification = new EmailVerification("relationship@example.com", "123456", relationshipTestUser);
            verification = emailVerificationRepository.saveAndFlush(verification);

            // Verify the verification was saved and has correct relationship
            assertNotNull(verification.getId());
            assertEquals(relationshipTestUser.getId(), verification.getUser().getId());
            assertEquals(relationshipTestUser.getEmail(), verification.getUser().getEmail());
            
            // Verify we can find the verification
            List<EmailVerification> verifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("relationship@example.com");
            assertEquals(1, verifications.size());
            assertEquals(verification.getId(), verifications.get(0).getId());
            
            // Clean up - delete verification first, then user
            emailVerificationRepository.delete(verification);
            userRepository.delete(relationshipTestUser);
            
            // Verify cleanup worked
            List<EmailVerification> verificationsAfter = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("relationship@example.com");
            assertTrue(verificationsAfter.isEmpty());
        }

        @Test
        @DisplayName("Should handle multiple verifications per user")
        void testMultipleVerificationsPerUser() {
            // Save first verification
            emailVerificationRepository.save(testVerification);
            
            // Create and save second verification for same user
            EmailVerification secondVerification = new EmailVerification("test@example.com", "654321", testUser);
            emailVerificationRepository.save(secondVerification);
            entityManager.flush();

            List<EmailVerification> userVerifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("test@example.com");
            assertEquals(2, userVerifications.size());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Conditions")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle non-existent email search")
        void testNonExistentEmailSearch() {
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            List<EmailVerification> verifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("nonexistent@example.com");
            assertTrue(verifications.isEmpty());
        }

        @Test
        @DisplayName("Should handle non-existent code search")
        void testNonExistentCodeSearch() {
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            Optional<EmailVerification> verification = emailVerificationRepository
                    .findByEmailAndVerificationCodeAndUsedFalse("test@example.com", "wrong-code");
            assertFalse(verification.isPresent());
        }

        @Test
        @DisplayName("Should handle empty email search")
        void testEmptyEmailSearch() {
            List<EmailVerification> verifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("");
            assertTrue(verifications.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty database")
        void testEmptyDatabase() {
            List<EmailVerification> allVerifications = emailVerificationRepository.findAll();
            assertTrue(allVerifications.isEmpty());

            List<EmailVerification> expiredVerifications = emailVerificationRepository
                    .findByExpiresAtBeforeAndUsedFalse(LocalDateTime.now());
            assertTrue(expiredVerifications.isEmpty());
        }
    }

    @Nested
    @DisplayName("Modifying Operations")
    class ModifyingOperationsTests {

        @Test
        @DisplayName("Should mark all unused verifications as used by email")
        void testMarkAllAsUsedByEmail() {
            // Create multiple unused verifications for same email
            emailVerificationRepository.save(testVerification);
            
            EmailVerification secondVerification = new EmailVerification("test@example.com", "654321", testUser);
            emailVerificationRepository.save(secondVerification);
            
            // Create verification for different email (should not be affected)
            EmailVerification otherEmailVerification = new EmailVerification("other@example.com", "999999", testUser);
            emailVerificationRepository.save(otherEmailVerification);
            
            entityManager.flush();

            // Mark all as used for specific email
            emailVerificationRepository.markAllAsUsedByEmail("test@example.com");
            entityManager.flush();
            entityManager.clear();

            // Verify that verifications for test@example.com are marked as used
            List<EmailVerification> testEmailVerifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("test@example.com");
            assertEquals(2, testEmailVerifications.size());
            assertTrue(testEmailVerifications.get(0).isUsed());
            assertTrue(testEmailVerifications.get(1).isUsed());

            // Verify that other email verification is not affected
            List<EmailVerification> otherEmailVerifications = emailVerificationRepository
                    .findByEmailOrderByCreatedAtDesc("other@example.com");
            assertEquals(1, otherEmailVerifications.size());
            assertFalse(otherEmailVerifications.get(0).isUsed());
        }

        @Test
        @DisplayName("Should delete expired verifications")
        void testDeleteExpiredVerifications() {
            // Create expired verification
            EmailVerification expiredVerification = new EmailVerification("expired@example.com", "111111", testUser);
            expiredVerification.setExpiresAt(LocalDateTime.now().minusHours(1));
            emailVerificationRepository.save(expiredVerification);
            
            // Create non-expired verification
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            long countBefore = emailVerificationRepository.count();
            assertEquals(2, countBefore);

            // Delete expired verifications
            emailVerificationRepository.deleteExpiredVerifications(LocalDateTime.now());
            entityManager.flush();

            long countAfter = emailVerificationRepository.count();
            assertEquals(1, countAfter);

            // Verify that only non-expired verification remains
            List<EmailVerification> remaining = emailVerificationRepository.findAll();
            assertEquals(1, remaining.size());
            assertEquals("test@example.com", remaining.get(0).getEmail());
        }

        @Test
        @DisplayName("Should handle marking as used when no verifications exist")
        void testMarkAllAsUsedByEmail_NoVerifications() {
            // Try to mark as used for non-existent email
            emailVerificationRepository.markAllAsUsedByEmail("nonexistent@example.com");
            entityManager.flush();

            // Should not throw exception and should not affect anything
            long count = emailVerificationRepository.count();
            assertEquals(0, count);
        }

        @Test
        @DisplayName("Should handle deleting expired when none exist")
        void testDeleteExpiredVerifications_NoneExpired() {
            // Save non-expired verification
            emailVerificationRepository.save(testVerification);
            entityManager.flush();

            long countBefore = emailVerificationRepository.count();
            assertEquals(1, countBefore);

            // Try to delete expired verifications
            emailVerificationRepository.deleteExpiredVerifications(LocalDateTime.now());
            entityManager.flush();

            long countAfter = emailVerificationRepository.count();
            assertEquals(1, countAfter); // Should remain unchanged
        }
    }

    @Nested
    @DisplayName("Performance and Constraints")
    class PerformanceTests {

        @Test
        @DisplayName("Should enforce database constraints")
        void testDatabaseConstraints() {
            // Test that required fields are enforced
            EmailVerification invalidVerification = new EmailVerification();
            invalidVerification.setUser(testUser);
            // Missing email and verification code

            assertThrows(Exception.class, () -> {
                emailVerificationRepository.save(invalidVerification);
                entityManager.flush();
            });
        }

        @Test
        @DisplayName("Should handle large verification codes")
        void testLargeVerificationCode() {
            String largeCode = "A".repeat(1000); // Very large code
            testVerification.setVerificationCode(largeCode);

            // This might fail due to database column size constraints
            assertThrows(Exception.class, () -> {
                emailVerificationRepository.save(testVerification);
                entityManager.flush();
            });
        }

        @Test
        @DisplayName("Should handle concurrent access")
        void testConcurrentAccess() {
            EmailVerification saved = emailVerificationRepository.save(testVerification);
            entityManager.flush();

            // Simulate concurrent modification
            EmailVerification verification1 = emailVerificationRepository.findById(saved.getId()).orElse(null);
            EmailVerification verification2 = emailVerificationRepository.findById(saved.getId()).orElse(null);

            assertNotNull(verification1);
            assertNotNull(verification2);

            verification1.setUsed(true);
            verification2.setVerificationCode("new-code");

            emailVerificationRepository.save(verification1);
            emailVerificationRepository.save(verification2);

            // The last save should win
            EmailVerification final_verification = emailVerificationRepository.findById(saved.getId()).orElse(null);
            assertNotNull(final_verification);
            assertEquals("new-code", final_verification.getVerificationCode());
        }
    }
}