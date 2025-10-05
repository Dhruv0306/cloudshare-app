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
 * Updated to reflect field name change from 'code' to 'verificationCode'
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
        assertNull(request.getVerificationCode());
    }

    @Test
    @DisplayName("Should create instance with parameterized constructor")
    void shouldCreateInstanceWithParameterizedConstructor() {
        String email = "test@example.com";
        String verificationCode = "123456";

        VerifyEmailRequest request = new VerifyEmailRequest(email, verificationCode);

        assertNotNull(request);
        assertEquals(email, request.getEmail());
        assertEquals(verificationCode, request.getVerificationCode());
    }

    @Test
    @DisplayName("Should create instance with parameterized constructor using null values")
    void shouldCreateInstanceWithParameterizedConstructorUsingNullValues() {
        VerifyEmailRequest request = new VerifyEmailRequest(null, null);

        assertNotNull(request);
        assertNull(request.getEmail());
        assertNull(request.getVerificationCode());
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
    void shouldSetAndgetVerificationCodeCorrectly() {
        String code = "654321";
        request.setVerificationCode(code);
        assertEquals(code, request.getVerificationCode());
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
        request.setVerificationCode(null);
        assertNull(request.getVerificationCode());
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
        request.setVerificationCode("123456");

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
        request.setVerificationCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")),
                "Invalid email should have validation errors: " + invalidEmail);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject null and empty email")
    void shouldRejectNullAndEmptyEmail(String email) {
        request.setEmail(email);
        request.setVerificationCode("123456");

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
        request.setVerificationCode(validCode);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
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
            "a123456", // letter + 6 digits
            "123456789", // 9 digits
            "1", // single digit
            "12", // 2 digits
            "123", // 3 digits
            "1234", // 4 digits
            "12345678", // 8 digits
            "prefix123456", // 6 digits with prefix
            "123456suffix", // 6 digits with suffix
            "pre123456suf", // 6 digits with prefix and suffix
            "123-456", // 6 digits with hyphen
            "123 456", // 6 digits with space
            "123.456", // 6 digits with dot
            "123_456", // 6 digits with underscore
            "123/456", // 6 digits with slash
            "123\\456", // 6 digits with backslash
            "123+456", // 6 digits with plus
            "123*456", // 6 digits with asterisk
            "123#456", // 6 digits with hash
            "123@456", // 6 digits with at symbol
            "123$456", // 6 digits with dollar
            "123%456", // 6 digits with percent
            "123^456", // 6 digits with caret
            "123&456", // 6 digits with ampersand
            "123(456)", // 6 digits with parentheses
            "[123456]", // 6 digits with brackets
            "{123456}", // 6 digits with braces
            "\"123456\"", // 6 digits with quotes
            "'123456'", // 6 digits with single quotes
            "`123456`", // 6 digits with backticks
            "~123456", // 6 digits with tilde
            "!123456", // 6 digits with exclamation
            "?123456", // 6 digits with question mark
            ":123456", // 6 digits with colon
            ";123456", // 6 digits with semicolon
            ",123456", // 6 digits with comma
            ".123456", // 6 digits with leading dot
            "123456.", // 6 digits with trailing dot
            " 123456", // 6 digits with leading space
            "123456 ", // 6 digits with trailing space
            "\t123456", // 6 digits with leading tab
            "123456\t", // 6 digits with trailing tab
            "\n123456", // 6 digits with leading newline
            "123456\n", // 6 digits with trailing newline
            "\r123456", // 6 digits with leading carriage return
            "123456\r" // 6 digits with trailing carriage return
    })
    @DisplayName("Should reject invalid code formats")
    void shouldRejectInvalidCodeFormats(String invalidCode) {
        request.setEmail("test@example.com");
        request.setVerificationCode(invalidCode);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Invalid code should have validation errors: " + invalidCode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject null and empty code")
    void shouldRejectNullAndEmptyCode(String code) {
        request.setEmail("test@example.com");
        request.setVerificationCode(code);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Null or empty code should have validation errors");
    }

    // Combined Validation Tests
    @Test
    @DisplayName("Should pass validation with valid email and code")
    void shouldPassValidationWithValidEmailAndCode() {
        request.setEmail("user@example.com");
        request.setVerificationCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "Valid request should have no validation errors");
    }

    @Test
    @DisplayName("Should fail validation with both invalid email and code")
    void shouldFailValidationWithBothInvalidEmailAndCode() {
        request.setEmail("invalid-email");
        request.setVerificationCode("invalid-code");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.size() >= 2, "Should have at least 2 validation errors");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")));
    }

    @Test
    @DisplayName("Should fail validation with null values")
    void shouldFailValidationWithNullValues() {
        request.setEmail(null);
        request.setVerificationCode(null);

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.size() >= 2, "Should have at least 2 validation errors for null values");
    }

    // Validation Message Tests
    @Test
    @DisplayName("Should provide correct validation message for invalid code pattern")
    void shouldProvideCorrectValidationMessageForInvalidCodePattern() {
        request.setEmail("test@example.com");
        request.setVerificationCode("12345"); // Invalid: only 5 digits

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        ConstraintViolation<VerifyEmailRequest> codeViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("verificationCode"))
                .findFirst()
                .orElse(null);

        assertNotNull(codeViolation);
        assertEquals("Verification code must be exactly 6 digits", codeViolation.getMessage());
    }

    @Test
    @DisplayName("Should provide correct validation message for blank email")
    void shouldProvideCorrectValidationMessageForBlankEmail() {
        request.setEmail("");
        request.setVerificationCode("123456");

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
        request.setVerificationCode("123456");

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
        request.setVerificationCode("");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        ConstraintViolation<VerifyEmailRequest> codeViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("verificationCode"))
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
        request.setVerificationCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        // Should still validate as a proper email format
        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    @DisplayName("Should handle email with special characters")
    void shouldHandleEmailWithSpecialCharacters() {
        request.setEmail("user+test@example-domain.co.uk");
        request.setVerificationCode("123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "Email with valid special characters should pass validation");
    }

    @Test
    @DisplayName("Should handle code with leading zeros")
    void shouldHandleCodeWithLeadingZeros() {
        request.setEmail("test@example.com");
        request.setVerificationCode("000123");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with leading zeros should be valid");
    }

    // Object Behavior Tests
    @Test
    @DisplayName("Should maintain field values after setting")
    void shouldMaintainFieldValuesAfterSetting() {
        String email = "test@example.com";
        String code = "123456";

        request.setEmail(email);
        request.setVerificationCode(code);

        // Values should remain unchanged
        assertEquals(email, request.getEmail());
        assertEquals(code, request.getVerificationCode());

        // Setting new values should update correctly
        String newEmail = "new@example.com";
        String newCode = "654321";

        request.setEmail(newEmail);
        request.setVerificationCode(newCode);

        assertEquals(newEmail, request.getEmail());
        assertEquals(newCode, request.getVerificationCode());
    }

    @Test
    @DisplayName("Should handle empty string values")
    void shouldHandleEmptyStringValues() {
        request.setEmail("");
        request.setVerificationCode("");

        assertEquals("", request.getEmail());
        assertEquals("", request.getVerificationCode());

        // Empty strings should fail validation
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);
        assertTrue(violations.size() >= 2);
    }

    @Test
    @DisplayName("Should handle whitespace in fields")
    void shouldHandleWhitespaceInFields() {
        request.setEmail("  test@example.com  ");
        request.setVerificationCode("  123456  ");

        // Values should be stored as-is (trimming is typically done at service layer)
        assertEquals("  test@example.com  ", request.getEmail());
        assertEquals("  123456  ", request.getVerificationCode());

        // Validation should fail for code with spaces
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")));
    }

    // Strict Regex Pattern Tests (for ^\\d{6}$ pattern)
    @Test
    @DisplayName("Should reject code with 6 digits but additional characters at start")
    void shouldRejectCodeWith6DigitsButAdditionalCharactersAtStart() {
        request.setEmail("test@example.com");
        request.setVerificationCode("a123456");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with prefix should be rejected by strict regex");
    }

    @Test
    @DisplayName("Should reject code with 6 digits but additional characters at end")
    void shouldRejectCodeWith6DigitsButAdditionalCharactersAtEnd() {
        request.setEmail("test@example.com");
        request.setVerificationCode("123456a");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with suffix should be rejected by strict regex");
    }

    @Test
    @DisplayName("Should reject code with 6 digits embedded in longer string")
    void shouldRejectCodeWith6DigitsEmbeddedInLongerString() {
        request.setEmail("test@example.com");
        request.setVerificationCode("abc123456def");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with 6 digits embedded in longer string should be rejected");
    }

    @Test
    @DisplayName("Should reject code with whitespace around 6 digits")
    void shouldRejectCodeWithWhitespaceAround6Digits() {
        request.setEmail("test@example.com");
        request.setVerificationCode(" 123456 ");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with surrounding whitespace should be rejected");
    }

    @Test
    @DisplayName("Should reject code with tab characters around 6 digits")
    void shouldRejectCodeWithTabCharactersAround6Digits() {
        request.setEmail("test@example.com");
        request.setVerificationCode("\t123456\t");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with surrounding tabs should be rejected");
    }

    @Test
    @DisplayName("Should reject code with newline characters around 6 digits")
    void shouldRejectCodeWithNewlineCharactersAround6Digits() {
        request.setEmail("test@example.com");
        request.setVerificationCode("\n123456\n");

        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                "Code with surrounding newlines should be rejected");
    }

    @Test
    @DisplayName("Should accept exactly 6 digits with no additional characters")
    void shouldAcceptExactly6DigitsWithNoAdditionalCharacters() {
        String[] validCodes = {
                "000000", "111111", "222222", "333333", "444444", "555555",
                "666666", "777777", "888888", "999999", "123456", "654321",
                "987654", "135792", "246810", "098765", "012345", "543210"
        };

        for (String validCode : validCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(validCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Valid 6-digit code should pass validation: " + validCode);
        }
    }

    @Test
    @DisplayName("Should verify regex pattern matches exactly 6 digits boundary")
    void shouldVerifyRegexPatternMatchesExactly6DigitsBoundary() {
        // Test boundary conditions for the strict regex ^\\d{6}$
        String[] invalidCodes = {
                "12345", // 5 digits - too short
                "1234567", // 7 digits - too long
                "x123456", // prefix
                "123456x", // suffix
                "12345x", // 5 digits + char
                "x12345", // char + 5 digits
                "1234567x", // 7 digits + char
                "x1234567" // char + 7 digits
        };

        for (String invalidCode : invalidCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(invalidCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Invalid code should be rejected by strict regex: " + invalidCode);
        }
    }

    // Integration-style Tests
    @Test
    @DisplayName("Should work correctly in typical usage scenario")
    void shouldWorkCorrectlyInTypicalUsageScenario() {
        // Simulate typical controller usage
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("user@company.com");
        request.setVerificationCode("987654");

        // Validate the request
        Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

        // Should pass validation
        assertTrue(violations.isEmpty());

        // Should have correct values
        assertEquals("user@company.com", request.getEmail());
        assertEquals("987654", request.getVerificationCode());
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
        assertEquals("555555", request.getVerificationCode());
    }

    // Additional Edge Cases for Strict Regex
    @Test
    @DisplayName("Should reject code with Unicode digits")
    void shouldRejectCodeWithUnicodeDigits() {
        // Unicode digits that look like regular digits but aren't ASCII 0-9
        String[] unicodeCodes = {
                "１２３４５６", // Full-width digits
                "𝟎𝟏𝟐𝟑𝟒𝟓", // Mathematical bold digits
                "𝟘𝟙𝟚𝟛𝟜𝟝" // Mathematical double-struck digits
        };

        for (String unicodeCode : unicodeCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(unicodeCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Unicode digits should be rejected: " + unicodeCode);
        }
    }

    @Test
    @DisplayName("Should reject code with mixed ASCII and non-ASCII characters")
    void shouldRejectCodeWithMixedAsciiAndNonAsciiCharacters() {
        String[] mixedCodes = {
                "12345６", // 5 ASCII digits + 1 full-width digit
                "１23456", // 1 full-width digit + 5 ASCII digits
                "123４56", // Mixed ASCII and full-width digits
                "12３４56", // Multiple mixed digits
                "123456ａ", // 6 digits + full-width letter
                "ａ123456" // Full-width letter + 6 digits
        };

        for (String mixedCode : mixedCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(mixedCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Mixed ASCII/non-ASCII code should be rejected: " + mixedCode);
        }
    }

    @Test
    @DisplayName("Should reject code with control characters")
    void shouldRejectCodeWithControlCharacters() {
        String[] controlCharCodes = {
                "123456\u0000", // Null character
                "\u0001123456", // Start of heading
                "123\u0002456", // Start of text
                "123456\u0003", // End of text
                "123456\u0004", // End of transmission
                "123456\u0005", // Enquiry
                "123456\u0006", // Acknowledge
                "123456\u0007", // Bell
                "123456\u0008", // Backspace
                "123456\u000B", // Vertical tab
                "123456\u000C", // Form feed
                "123456\u000E", // Shift out
                "123456\u000F", // Shift in
                "123456\u007F" // Delete
        };

        for (String controlCharCode : controlCharCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(controlCharCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Code with control characters should be rejected");
        }
    }

    @Test
    @DisplayName("Should handle all valid 6-digit combinations systematically")
    void shouldHandleAllValid6DigitCombinationsSystematically() {
        // Test systematic patterns to ensure regex works correctly
        String[] systematicCodes = {
                "000000", "000001", "000010", "000100", "001000", "010000", "100000",
                "111111", "222222", "333333", "444444", "555555", "666666", "777777", "888888", "999999",
                "123456", "234567", "345678", "456789", "567890", "678901", "789012", "890123", "901234",
                "987654", "876543", "765432", "654321", "543210", "432109", "321098", "210987", "109876",
                "135792", "246813", "357924", "468135", "579246", "680357", "791468", "802579", "913680",
                "024681", "135790", "246801", "357912", "468023", "579134", "680245", "791356", "802467"
        };

        for (String validCode : systematicCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(validCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Valid systematic 6-digit code should pass: " + validCode);
        }
    }

    @Test
    @DisplayName("Should verify regex anchors work correctly")
    void shouldVerifyRegexAnchorsWorkCorrectly() {
        // Test that ^ and $ anchors in the regex work as expected
        // These should all fail because they don't match the exact pattern ^\\d{6}$
        String[] anchorTestCodes = {
                "x123456x", // Characters before and after
                "123456x789", // Valid 6 digits but with extra content
                "abc123456", // Valid 6 digits but with prefix
                "123456def", // Valid 6 digits but with suffix
                "12345612345", // 6 digits repeated
                "1234561234", // 6 digits + 4 more
                "12123456", // 8 digits starting with valid 6
                "12345678", // 8 digits ending with valid 6
                "1234567890", // 10 digits containing valid 6
                " 123456", // Leading space
                "123456 ", // Trailing space
                "\t123456\t", // Leading and trailing tabs
                "\n123456\n", // Leading and trailing newlines
                "\r123456\r" // Leading and trailing carriage returns
        };

        for (String anchorTestCode : anchorTestCodes) {
            request.setEmail("test@example.com");
            request.setVerificationCode(anchorTestCode);

            Set<ConstraintViolation<VerifyEmailRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode")),
                    "Code should be rejected due to regex anchors: '" + anchorTestCode + "'");
        }
    }
}
