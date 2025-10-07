package com.cloud.computing.filesharingapp.dto;

import com.cloud.computing.filesharingapp.entity.SharePermission;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShareRequest DTO.
 * 
 * <p>This test class validates the ShareRequest data transfer object,
 * including validation constraints, getters/setters, and constructors.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@DisplayName("ShareRequest DTO Tests")
class ShareRequestTest {

    private Validator validator;
    private ShareRequest shareRequest;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        shareRequest = new ShareRequest();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create ShareRequest with default constructor")
        void shouldCreateShareRequestWithDefaultConstructor() {
            ShareRequest request = new ShareRequest();
            
            assertNull(request.getPermission());
            assertNull(request.getExpiresAt());
            assertNull(request.getRecipientEmails());
            assertNull(request.getMaxAccess());
            assertFalse(request.isSendNotification());
        }

        @Test
        @DisplayName("Should create ShareRequest with permission constructor")
        void shouldCreateShareRequestWithPermissionConstructor() {
            ShareRequest request = new ShareRequest(SharePermission.DOWNLOAD);
            
            assertEquals(SharePermission.DOWNLOAD, request.getPermission());
            assertNull(request.getExpiresAt());
            assertNull(request.getRecipientEmails());
            assertNull(request.getMaxAccess());
            assertFalse(request.isSendNotification());
        }

        @Test
        @DisplayName("Should create ShareRequest with VIEW_ONLY permission")
        void shouldCreateShareRequestWithViewOnlyPermission() {
            ShareRequest request = new ShareRequest(SharePermission.VIEW_ONLY);
            
            assertEquals(SharePermission.VIEW_ONLY, request.getPermission());
        }
    }

    @Nested
    @DisplayName("Permission Tests")
    class PermissionTests {

        @Test
        @DisplayName("Should set and get permission correctly")
        void shouldSetAndGetPermission() {
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            assertEquals(SharePermission.DOWNLOAD, shareRequest.getPermission());
            
            shareRequest.setPermission(SharePermission.VIEW_ONLY);
            assertEquals(SharePermission.VIEW_ONLY, shareRequest.getPermission());
        }

        @Test
        @DisplayName("Should allow null permission")
        void shouldAllowNullPermission() {
            shareRequest.setPermission(null);
            assertNull(shareRequest.getPermission());
        }
    }

    @Nested
    @DisplayName("Expiration Tests")
    class ExpirationTests {

        @Test
        @DisplayName("Should set and get expiration date correctly")
        void shouldSetAndGetExpirationDate() {
            LocalDateTime expiration = LocalDateTime.now().plusDays(7);
            shareRequest.setExpiresAt(expiration);
            assertEquals(expiration, shareRequest.getExpiresAt());
        }

        @Test
        @DisplayName("Should allow null expiration date")
        void shouldAllowNullExpirationDate() {
            shareRequest.setExpiresAt(null);
            assertNull(shareRequest.getExpiresAt());
        }

        @Test
        @DisplayName("Should handle past expiration date")
        void shouldHandlePastExpirationDate() {
            LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
            shareRequest.setExpiresAt(pastDate);
            assertEquals(pastDate, shareRequest.getExpiresAt());
        }

        @Test
        @DisplayName("Should handle future expiration date")
        void shouldHandleFutureExpirationDate() {
            LocalDateTime futureDate = LocalDateTime.now().plusYears(1);
            shareRequest.setExpiresAt(futureDate);
            assertEquals(futureDate, shareRequest.getExpiresAt());
        }
    }

    @Nested
    @DisplayName("Recipient Email Tests")
    class RecipientEmailTests {

        @Test
        @DisplayName("Should set and get recipient emails correctly")
        void shouldSetAndGetRecipientEmails() {
            List<String> emails = Arrays.asList("user1@example.com", "user2@example.com");
            shareRequest.setRecipientEmails(emails);
            assertEquals(emails, shareRequest.getRecipientEmails());
        }

        @Test
        @DisplayName("Should allow null recipient emails")
        void shouldAllowNullRecipientEmails() {
            shareRequest.setRecipientEmails(null);
            assertNull(shareRequest.getRecipientEmails());
        }

        @Test
        @DisplayName("Should allow empty recipient emails list")
        void shouldAllowEmptyRecipientEmailsList() {
            List<String> emptyList = Arrays.asList();
            shareRequest.setRecipientEmails(emptyList);
            assertEquals(emptyList, shareRequest.getRecipientEmails());
            assertTrue(shareRequest.getRecipientEmails().isEmpty());
        }

        @Test
        @DisplayName("Should handle single recipient email")
        void shouldHandleSingleRecipientEmail() {
            List<String> singleEmail = Arrays.asList("user@example.com");
            shareRequest.setRecipientEmails(singleEmail);
            assertEquals(1, shareRequest.getRecipientEmails().size());
            assertEquals("user@example.com", shareRequest.getRecipientEmails().get(0));
        }
    }

    @Nested
    @DisplayName("Max Access Tests")
    class MaxAccessTests {

        @Test
        @DisplayName("Should set and get max access correctly")
        void shouldSetAndGetMaxAccess() {
            shareRequest.setMaxAccess(10);
            assertEquals(10, shareRequest.getMaxAccess());
        }

        @Test
        @DisplayName("Should allow null max access")
        void shouldAllowNullMaxAccess() {
            shareRequest.setMaxAccess(null);
            assertNull(shareRequest.getMaxAccess());
        }

        @Test
        @DisplayName("Should handle large max access values")
        void shouldHandleLargeMaxAccessValues() {
            shareRequest.setMaxAccess(Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, shareRequest.getMaxAccess());
        }

        @Test
        @DisplayName("Should handle small positive max access values")
        void shouldHandleSmallPositiveMaxAccessValues() {
            shareRequest.setMaxAccess(1);
            assertEquals(1, shareRequest.getMaxAccess());
        }
    }

    @Nested
    @DisplayName("Send Notification Tests")
    class SendNotificationTests {

        @Test
        @DisplayName("Should set and get send notification correctly")
        void shouldSetAndGetSendNotification() {
            shareRequest.setSendNotification(true);
            assertTrue(shareRequest.isSendNotification());
            
            shareRequest.setSendNotification(false);
            assertFalse(shareRequest.isSendNotification());
        }

        @Test
        @DisplayName("Should default to false for send notification")
        void shouldDefaultToFalseForSendNotification() {
            ShareRequest request = new ShareRequest();
            assertFalse(request.isSendNotification());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            shareRequest.setExpiresAt(LocalDateTime.now().plusDays(7));
            shareRequest.setRecipientEmails(Arrays.asList("user@example.com"));
            shareRequest.setMaxAccess(10);
            shareRequest.setSendNotification(true);

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should fail validation when permission is null")
        void shouldFailValidationWhenPermissionIsNull() {
            shareRequest.setPermission(null);

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertEquals(1, violations.size());
            
            ConstraintViolation<ShareRequest> violation = violations.iterator().next();
            assertEquals("Permission is required", violation.getMessage());
            assertEquals("permission", violation.getPropertyPath().toString());
        }

        @Test
        @DisplayName("Should fail validation with invalid email format")
        void shouldFailValidationWithInvalidEmailFormat() {
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            shareRequest.setRecipientEmails(Arrays.asList("invalid-email"));

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertEquals(1, violations.size());
            
            ConstraintViolation<ShareRequest> violation = violations.iterator().next();
            assertEquals("Invalid email format", violation.getMessage());
        }

        @Test
        @DisplayName("Should fail validation with zero max access")
        void shouldFailValidationWithZeroMaxAccess() {
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            shareRequest.setMaxAccess(0);

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertEquals(1, violations.size());
            
            ConstraintViolation<ShareRequest> violation = violations.iterator().next();
            assertEquals("Maximum access count must be positive", violation.getMessage());
        }

        @Test
        @DisplayName("Should fail validation with negative max access")
        void shouldFailValidationWithNegativeMaxAccess() {
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            shareRequest.setMaxAccess(-1);

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertEquals(1, violations.size());
            
            ConstraintViolation<ShareRequest> violation = violations.iterator().next();
            assertEquals("Maximum access count must be positive", violation.getMessage());
        }

        @Test
        @DisplayName("Should pass validation with multiple valid emails")
        void shouldPassValidationWithMultipleValidEmails() {
            shareRequest.setPermission(SharePermission.VIEW_ONLY);
            shareRequest.setRecipientEmails(Arrays.asList(
                "user1@example.com", 
                "user2@test.org", 
                "admin@company.net"
            ));

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should fail validation with mixed valid and invalid emails")
        void shouldFailValidationWithMixedValidAndInvalidEmails() {
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            shareRequest.setRecipientEmails(Arrays.asList(
                "valid@example.com", 
                "invalid-email", 
                "another@test.org"
            ));

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertEquals(1, violations.size());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should create complete share request with all fields")
        void shouldCreateCompleteShareRequestWithAllFields() {
            LocalDateTime expiration = LocalDateTime.now().plusDays(30);
            List<String> emails = Arrays.asList("user1@example.com", "user2@example.com");
            
            shareRequest.setPermission(SharePermission.DOWNLOAD);
            shareRequest.setExpiresAt(expiration);
            shareRequest.setRecipientEmails(emails);
            shareRequest.setMaxAccess(100);
            shareRequest.setSendNotification(true);

            assertEquals(SharePermission.DOWNLOAD, shareRequest.getPermission());
            assertEquals(expiration, shareRequest.getExpiresAt());
            assertEquals(emails, shareRequest.getRecipientEmails());
            assertEquals(100, shareRequest.getMaxAccess());
            assertTrue(shareRequest.isSendNotification());

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should create minimal valid share request")
        void shouldCreateMinimalValidShareRequest() {
            shareRequest.setPermission(SharePermission.VIEW_ONLY);

            assertEquals(SharePermission.VIEW_ONLY, shareRequest.getPermission());
            assertNull(shareRequest.getExpiresAt());
            assertNull(shareRequest.getRecipientEmails());
            assertNull(shareRequest.getMaxAccess());
            assertFalse(shareRequest.isSendNotification());

            Set<ConstraintViolation<ShareRequest>> violations = validator.validate(shareRequest);
            assertTrue(violations.isEmpty());
        }
    }
}