package com.cloud.computing.filesharingapp.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for VerifyEmailRequest DTO
 * Tests constructor behavior, getters/setters, validation constraints, and edge
 * cases
 */
@DisplayName("VerifyEmailRequest DTO Tests")
class VerifyEmailRequestTest {

    private Validator validator;
    private VerifyEmailRequest request;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        request = new VerifyEmailRequest();
    }

    // Constructor Tests
    @Test
    @DisplayName("Should create instance with default constructor")
    void shouldCreateInstanceWithDefaultConstructor() {
        VerifyEmailRequest request = new VerifyEmailRequest();

        assertNotNull(request);
        assertNull(request.getEmail());
        assertNull(request.getCode());
    }

    @Test
    @DisplayName("Should create instance with parameterized constructor")
    void shouldCreateInstanceWithParameterizedConstructor() {
        String email = "test@example.com";
        String code = "123456";

        VerifyEmailRequest request = new VerifyEmailRequest(email, code);

        assertNotNull(request);
        assertEquals(email, request.getEmail());
        assertEquals(code, request.getCode());
    }

    @Test
    @DisplayName("Should create instance with parameterized constructor using null values")
    void shouldCreateInstanceWithParameterizedConstructorUsingNullValues() {
        VerifyEmailRequest request = new VerifyEmailRequest(null, null);

        assertNotNull(request);
        assertNull(request.getEmail());
        assertNull(request.getCode());
    }

    // Getter and Setter Tests
    @Test
    @DisplayName("Should set and get email correctly")
    void shouldSetAndGetEmailCorrectly() {
        String email = "user@domain.com";
        request.setEmail(email);
        assertEquals(email, request.getEmail());
    }

    @Test
    @DisplayName("Should set and get code correctly")
    void shouldSetAndGetCodeCorrectly() {
        String code = "654321";
        request.setCode(code);
        assertEquals(code, request.getCode());
    }

    @Test
    @DisplayName("Should handle null email")
    void shouldHandleNullEmail() {
        request.setEmail(null);
        assertNull(request.getEmail());
    }

    @Test
    @DisplayName("Should handle null code")
    void shouldHandleNullCode() {
        request.setCode(null);
        assertNull(request.getCode());
    }

    // Email Validation Tests
    @ParameterizedTest
    @ValueSource(strings = {
            "user@example.com",
            "test.email@domain.co.uk",
            "user+tag@example.org",
            "firstname.lastname@company.com",
            "user123@test-domain.com",
            "a@b.co"
    })
    @DisplayName("Should accept valid email formats")
    void shouldAcceptValidEmailFormats(String validEmail) {
        request.setEmail(validEmail);
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("email")),
                "Valid email should not have validation errors: " + validEmail);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid-email",
            "@example.com",
            "user@",
            "user..name@example.com",
            "user@.com",
            "user name@example.com",
            "user@example.",
            ""
    })
    @DisplayName("Should reject invalid email formats")
    void shouldRejectInvalidEmailFormats(String invalidEmail) {
        request.setEmail(invalidEmail);
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")),
                "Invalid email should have validation errors: " + invalidEmail);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject null and empty email")
    void shouldRejectNullAndEmptyEmail(String email) {
        request.setEmail(email);
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")),
                "Null or empty email should have validation errors");
    }

    // Code Validation Tests
    @ParameterizedTest
    @ValueSource(strings = {
            "123456",
            "000000",
            "999999",
            "654321",
            "111111"
    })
    @DisplayName("Should accept valid 6-digit codes")
    void shouldAcceptValid6DigitCodes(String validCode) {
        request.setEmail("test@example.com");
        request.setCode(validCode);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("code")),
                "Valid 6-digit code should not have validation errors: " + validCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345", // 5 digits
            "1234567", // 7 digits
            "12345a", // contains letter
            "abcdef", // all letters
            "12 456", // contains space
            "12-456", // contains hyphen
            "123.456", // contains dot
            "123456a", // 6 digits + letter
            "a123456" // letter + 6 digits
    })
    @DisplayName("Should reject invalid code formats")
    void shouldRejectInvalidCodeFormats(String invalidCode) {
        request.setEmail("test@example.com");
        request.setCode(invalidCode);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("code")),
                "Invalid code should have validation errors: " + invalidCode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject null and empty code")
    void shouldRejectNullAndEmptyCode(String code) {
        request.setEmail("test@example.com");
        request.setCode(code);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("code")),
                "Null or empty code should have validation errors");
    }

    // Combined Validation Tests
    @Test
    @DisplayName("Should pass validation with valid email and code")
    void shouldPassValidationWithValidEmailAndCode() {
        request.setEmail("user@example.com");
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "Valid request should have no validation errors");
    }

    @Test
    @DisplayName("Should fail validation with both invalid email and code")
    void shouldFailValidationWithBothInvalidEmailAndCode() {
        request.setEmail("invalid-email");
        request.setCode("invalid-code");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.size() >= 2, "Should have at least 2 validation errors");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("code")));
    }

    @Test
    @DisplayName("Should fail validation with null values")
    void shouldFailValidationWithNullValues() {
        request.setEmail(null);
        request.setCode(null);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.size() >= 2, "Should have at least 2 validation errors for null values");
    }

    // Validation Message Tests
    @Test
    @DisplayName("Should provide correct validation message for invalid code pattern")
    void shouldProvideCorrectValidationMessageForInvalidCodePattern() {
        request.setEmail("test@example.com");
        request.setCode("12345"); // Invalid: only 5 digits

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        ConstraintViolation<VerifyEmailRequest> codeViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("code"))
                .findFirst()
                .orElse(null);

        assertNotNull(codeViolation);
        assertEquals("Verification code must be exactly 6 digits", codeViolation.getMessage());
    }

    @Test
    @DisplayName("Should provide correct validation message for blank email")
    void shouldProvideCorrectValidationMessageForBlankEmail() {
        request.setEmail("");
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        ConstraintViolation<VerifyEmailRequest> emailViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("email"))
                .findFirst()
                .orElse(null);

        assertNotNull(emailViolation);
        assertEquals("Email is required", emailViolation.getMessage());
    }

    @Test
    @DisplayName("Should provide correct validation message for invalid email format")
    void shouldProvideCorrectValidationMessageForInvalidEmailFormat() {
        request.setEmail("invalid-email");
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        ConstraintViolation<VerifyEmailRequest> emailViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("email"))
                .findFirst()
                .orElse(null);

        assertNotNull(emailViolation);
        assertEquals("Please provide a valid email address", emailViolation.getMessage());
    }

    @Test
    @DisplayName("Should provide correct validation message for blank code")
    void shouldProvideCorrectValidationMessageForBlankCode() {
        request.setEmail("test@example.com");
        request.setCode("");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        ConstraintViolation<VerifyEmailRequest> codeViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("code"))
                .findFirst()
                .orElse(null);

        assertNotNull(codeViolation);
        assertEquals("Verification code is required", codeViolation.getMessage());
    }

    // Edge Case Tests
    @Test
    @DisplayName("Should handle very long email addresses")
    void shouldHandleVeryLongEmailAddresses() {
        String longEmail = "a".repeat(50) + "@" + "b".repeat(50) + ".com";
        request.setEmail(longEmail);
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        // Should still validate as a proper email format
        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("Should handle email with special characters")
    void shouldHandleEmailWithSpecialCharacters() {
        request.setEmail("user+test@example-domain.co.uk");
        request.setCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "Email with valid special characters should pass validation");
    }

    @Test
    @DisplayName("Should handle code with leading zeros")
    void shouldHandleCodeWithLeadingZeros() {
        request.setEmail("test@example.com");
        request.setCode("000123");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("code")),
                "Code with leading zeros should be valid");
    }

    // Object Behavior Tests
    @Test
    @DisplayName("Should maintain field values after setting")
    void shouldMaintainFieldValuesAfterSetting() {
        String email = "test@example.com";
        String code = "123456";

        request.setEmail(email);
        request.setCode(code);

        // Values should remain unchanged
        assertEquals(email, request.getEmail());
        assertEquals(code, request.getCode());

        // Setting new values should update correctly
        String newEmail = "new@example.com";
        String newCode = "654321";

        request.setEmail(newEmail);
        request.setCode(newCode);

        assertEquals(newEmail, request.getEmail());
        assertEquals(newCode, request.getCode());
    }

    @Test
    @DisplayName("Should handle empty string values")
    void shouldHandleEmptyStringValues() {
        request.setEmail("");
        request.setCode("");

        assertEquals("", request.getEmail());
        assertEquals("", request.getCode());

        // Empty strings should fail validation
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);
        assertTrue(violations.size() >= 2);
    }

    @Test
    @DisplayName("Should handle whitespace in fields")
    void shouldHandleWhitespaceInFields() {
        request.setEmail("  test@example.com  ");
        request.setCode("  123456  ");

        // Values should be stored as-is (trimming is typically done at service layer)
        assertEquals("  test@example.com  ", request.getEmail());
        assertEquals("  123456  ", request.getCode());

        // Validation should fail for code with spaces
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("code")));
    }

    // Integration-style Tests
    @Test
    @DisplayName("Should work correctly in typical usage scenario")
    void shouldWorkCorrectlyInTypicalUsageScenario() {
        // Simulate typical controller usage
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("user@company.com");
        request.setCode("987654");

        // Validate the request
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        // Should pass validation
        assertTrue(violations.isEmpty());

        // Should have correct values
        assertEquals("user@company.com", request.getEmail());
        assertEquals("987654", request.getCode());
    }

    @Test
    @DisplayName("Should work correctly with constructor in typical usage")
    void shouldWorkCorrectlyWithConstructorInTypicalUsage() {
        // Simulate creating request with constructor
        VerifyEmailRequest request = new VerifyEmailRequest("admin@system.com", "555555");

        // Validate the request
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        // Should pass validation
        assertTrue(violations.isEmpty());

        // Should have correct values
        assertEquals("admin@system.com", request.getEmail());
        assertEquals("555555", request.getCode());
    }
}