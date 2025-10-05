package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.PasswordStrength;
import com.cloud.computing.filesharingapp.dto.PasswordStrengthResponse;
import com.cloud.computing.filesharingapp.exception.PasswordValidationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for evaluating password strength and providing validation
 */
@Service
public class PasswordStrengthService {
    
    private static final int MIN_LENGTH = 8;
    private static final int STRONG_LENGTH = 12;
    
    // Regex patterns for password validation
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]");
    
    /**
     * Evaluates password strength and returns detailed response
     * 
     * @param password the password to evaluate
     * @return PasswordStrengthResponse with strength level, score, requirements and suggestions
     */
    public PasswordStrengthResponse evaluatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return new PasswordStrengthResponse(
                PasswordStrength.WEAK, 
                0, 
                getRequirements(password),
                getSuggestions(password)
            );
        }
        
        PasswordStrength strength = determineStrength(password);
        int score = calculateScore(password);
        List<String> requirements = getRequirements(password);
        List<String> suggestions = getSuggestions(password);
        
        return new PasswordStrengthResponse(strength, score, requirements, suggestions);
    }
    
    /**
     * Checks if password meets minimum requirements
     * 
     * @param password the password to validate
     * @return true if password meets minimum requirements, false otherwise
     */
    public boolean meetsMinimumRequirements(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        
        return hasLowercase(password) && 
               hasUppercase(password) && 
               hasDigit(password);
    }
    
    /**
     * Validates password and throws exception if it doesn't meet minimum requirements
     * 
     * @param password the password to validate
     * @throws PasswordValidationException if password doesn't meet requirements
     */
    public void validatePasswordRequirements(String password) {
        if (!meetsMinimumRequirements(password)) {
            List<String> requirements = getRequirements(password);
            String message = "Password does not meet minimum requirements: " + String.join(", ", requirements);
            throw new PasswordValidationException(message);
        }
    }
    
    /**
     * Determines the strength level of a password based on requirements
     */
    private PasswordStrength determineStrength(String password) {
        // Requirement 2.2: When password length is less than 8 characters THEN system SHALL show "Weak"
        if (password.length() < MIN_LENGTH) {
            return PasswordStrength.WEAK;
        }
        
        boolean hasLower = hasLowercase(password);
        boolean hasUpper = hasUppercase(password);
        boolean hasDigit = hasDigit(password);
        boolean hasSpecial = hasSpecialChar(password);
        
        // Requirement 2.4: When password contains lowercase, uppercase, numbers, and special characters with 12+ length THEN system SHALL show "Strong"
        if (password.length() >= STRONG_LENGTH && hasLower && hasUpper && hasDigit && hasSpecial) {
            return PasswordStrength.STRONG;
        }
        
        // Requirement 2.3: When password contains lowercase, uppercase, and numbers THEN system SHALL show "Medium"
        if (hasLower && hasUpper && hasDigit) {
            return PasswordStrength.MEDIUM;
        }
        
        return PasswordStrength.WEAK;
    }
    
    /**
     * Calculates a numerical score (0-100) for password strength
     */
    private int calculateScore(String password) {
        int score = 0;
        
        // Length scoring
        if (password.length() >= MIN_LENGTH) {
            score += 25;
        }
        if (password.length() >= STRONG_LENGTH) {
            score += 15;
        }
        
        // Character type scoring
        if (hasLowercase(password)) score += 15;
        if (hasUppercase(password)) score += 15;
        if (hasDigit(password)) score += 15;
        if (hasSpecialChar(password)) score += 15;
        
        return Math.min(score, 100);
    }
    
    /**
     * Gets list of unmet requirements for the password
     */
    private List<String> getRequirements(String password) {
        List<String> requirements = new ArrayList<>();
        
        if (password == null || password.length() < MIN_LENGTH) {
            requirements.add("At least " + MIN_LENGTH + " characters long");
        }
        if (!hasLowercase(password)) {
            requirements.add("At least one lowercase letter");
        }
        if (!hasUppercase(password)) {
            requirements.add("At least one uppercase letter");
        }
        if (!hasDigit(password)) {
            requirements.add("At least one number");
        }
        if (!hasSpecialChar(password)) {
            requirements.add("At least one special character");
        }
        
        return requirements;
    }
    
    /**
     * Gets suggestions for improving password strength
     */
    private List<String> getSuggestions(String password) {
        List<String> suggestions = new ArrayList<>();
        
        if (password == null || password.isEmpty()) {
            suggestions.add("Create a password with at least " + MIN_LENGTH + " characters");
            return suggestions;
        }
        
        if (password.length() < STRONG_LENGTH) {
            suggestions.add("Use at least " + STRONG_LENGTH + " characters for stronger security");
        }
        
        if (!hasSpecialChar(password)) {
            suggestions.add("Add special characters (!@#$%^&*) for better security");
        }
        
        if (password.length() < MIN_LENGTH || !hasLowercase(password) || !hasUppercase(password) || !hasDigit(password)) {
            suggestions.add("Combine uppercase, lowercase, and numbers");
        }
        
        return suggestions;
    }
    
    // Helper methods for character type checking
    private boolean hasLowercase(String password) {
        return password != null && LOWERCASE_PATTERN.matcher(password).find();
    }
    
    private boolean hasUppercase(String password) {
        return password != null && UPPERCASE_PATTERN.matcher(password).find();
    }
    
    private boolean hasDigit(String password) {
        return password != null && DIGIT_PATTERN.matcher(password).find();
    }
    
    private boolean hasSpecialChar(String password) {
        return password != null && SPECIAL_CHAR_PATTERN.matcher(password).find();
    }
}