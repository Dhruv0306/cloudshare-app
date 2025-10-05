package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.PasswordStrength;
import com.cloud.computing.filesharingapp.dto.PasswordStrengthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PasswordStrengthService
 */
class PasswordStrengthServiceTest {
    
    private PasswordStrengthService passwordStrengthService;
    
    @BeforeEach
    void setUp() {
        passwordStrengthService = new PasswordStrengthService();
    }
    
    @Test
    void testWeakPasswordLessThan8Characters() {
        // Requirement 2.2: When password length is less than 8 characters THEN system SHALL show "Weak"
        String weakPassword = "Pass1";
        PasswordStrengthResponse response = passwordStrengthService.evaluatePassword(weakPassword);
        
        assertEquals(PasswordStrength.WEAK, response.getLevel());
        assertFalse(passwordStrengthService.meetsMinimumRequirements(weakPassword));
        assertTrue(response.getRequirements().contains("At least 8 characters long"));
    }
    
    @Test
    void testMediumPasswordWithLowercaseUppercaseNumbers() {
        // Requirement 2.3: When password contains lowercase, uppercase, and numbers THEN system SHALL show "Medium"
        String mediumPassword = "Password123";
        PasswordStrengthResponse response = passwordStrengthService.evaluatePassword(mediumPassword);
        
        assertEquals(PasswordStrength.MEDIUM, response.getLevel());
        assertTrue(passwordStrengthService.meetsMinimumRequirements(mediumPassword));
        assertTrue(response.getScore() > 50);
    }
    
    @Test
    void testStrongPasswordWithAllRequirements() {
        // Requirement 2.4: When password contains lowercase, uppercase, numbers, and special characters with 12+ length THEN system SHALL show "Strong"
        String strongPassword = "MyStrongPass123!";
        PasswordStrengthResponse response = passwordStrengthService.evaluatePassword(strongPassword);
        
        assertEquals(PasswordStrength.STRONG, response.getLevel());
        assertTrue(passwordStrengthService.meetsMinimumRequirements(strongPassword));
        assertTrue(response.getScore() >= 80);
        assertTrue(response.getRequirements().isEmpty());
    }
    
    @Test
    void testMinimumRequirementsValidation() {
        // Test various combinations for minimum requirements
        assertFalse(passwordStrengthService.meetsMinimumRequirements(null));
        assertFalse(passwordStrengthService.meetsMinimumRequirements(""));
        assertFalse(passwordStrengthService.meetsMinimumRequirements("short"));
        assertFalse(passwordStrengthService.meetsMinimumRequirements("nouppercase123"));
        assertFalse(passwordStrengthService.meetsMinimumRequirements("NOLOWERCASE123"));
        assertFalse(passwordStrengthService.meetsMinimumRequirements("NoNumbers"));
        
        assertTrue(passwordStrengthService.meetsMinimumRequirements("ValidPass123"));
    }
    
    @Test
    void testPasswordWithOnlySpecialCharacters() {
        String specialOnlyPassword = "!@#$%^&*";
        PasswordStrengthResponse response = passwordStrengthService.evaluatePassword(specialOnlyPassword);
        
        assertEquals(PasswordStrength.WEAK, response.getLevel());
        assertFalse(passwordStrengthService.meetsMinimumRequirements(specialOnlyPassword));
    }
    
    @Test
    void testEmptyAndNullPasswords() {
        PasswordStrengthResponse nullResponse = passwordStrengthService.evaluatePassword(null);
        PasswordStrengthResponse emptyResponse = passwordStrengthService.evaluatePassword("");
        
        assertEquals(PasswordStrength.WEAK, nullResponse.getLevel());
        assertEquals(PasswordStrength.WEAK, emptyResponse.getLevel());
        assertEquals(0, nullResponse.getScore());
        assertEquals(0, emptyResponse.getScore());
        
        assertFalse(passwordStrengthService.meetsMinimumRequirements(null));
        assertFalse(passwordStrengthService.meetsMinimumRequirements(""));
    }
    
    @Test
    void testPasswordRequirementsAndSuggestions() {
        String incompletePassword = "pass";
        PasswordStrengthResponse response = passwordStrengthService.evaluatePassword(incompletePassword);
        
        assertFalse(response.getRequirements().isEmpty());
        assertFalse(response.getSuggestions().isEmpty());
        
        assertTrue(response.getRequirements().contains("At least 8 characters long"));
        assertTrue(response.getRequirements().contains("At least one uppercase letter"));
        assertTrue(response.getRequirements().contains("At least one number"));
    }
    
    @Test
    void testScoreCalculation() {
        // Test score progression
        PasswordStrengthResponse weakResponse = passwordStrengthService.evaluatePassword("weak");
        PasswordStrengthResponse mediumResponse = passwordStrengthService.evaluatePassword("Password123");
        PasswordStrengthResponse strongResponse = passwordStrengthService.evaluatePassword("StrongPassword123!");
        
        assertTrue(weakResponse.getScore() < mediumResponse.getScore());
        assertTrue(mediumResponse.getScore() < strongResponse.getScore());
        assertTrue(strongResponse.getScore() <= 100);
    }
}