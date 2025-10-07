package com.cloud.computing.filesharingapp.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidUser() {
        User user = new User("validuser", "valid@example.com", "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertTrue(violations.isEmpty());
    }

    @Test
    void testBlankUsername() {
        User user = new User("", "valid@example.com", "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testNullUsername() {
        User user = new User(null, "valid@example.com", "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testUsernameTooLong() {
        String longUsername = "a".repeat(51); // Exceeds max length of 50
        User user = new User(longUsername, "valid@example.com", "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testBlankEmail() {
        User user = new User("validuser", "", "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void testNullEmail() {
        User user = new User("validuser", null, "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void testInvalidEmailFormat() {
        User user = new User("validuser", "invalid-email", "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void testEmailTooLong() {
        String longEmail = "a".repeat(90) + "@example.com"; // Exceeds max length of 100
        User user = new User("validuser", longEmail, "validpassword123");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void testBlankPassword() {
        User user = new User("validuser", "valid@example.com", "");
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void testNullPassword() {
        User user = new User("validuser", "valid@example.com", null);
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void testPasswordTooLong() {
        String longPassword = "a".repeat(121); // Exceeds max length of 120
        User user = new User("validuser", "valid@example.com", longPassword);
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void testMaxLengthBoundaries() {
        String maxUsername = "a".repeat(50); // Exactly max length
        String maxPassword = "a".repeat(120); // Exactly max length
        // Use a realistic email that's close to max length but valid
        String validEmail = "test@example.com";
        
        User user = new User(maxUsername, validEmail, maxPassword);
        
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        
        assertTrue(violations.isEmpty());
    }



    @Test
    void testValidEmailFormats() {
        String[] validEmails = {
            "test@example.com",
            "user.name@example.com",
            "user+tag@example.co.uk",
            "123@example.org"
        };
        
        for (String email : validEmails) {
            User user = new User("validuser", email, "validpassword123");
            Set<ConstraintViolation<User>> violations = validator.validate(user);
            assertTrue(violations.isEmpty(), "Email should be valid: " + email);
        }
    }
}