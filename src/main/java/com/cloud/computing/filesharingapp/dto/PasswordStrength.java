package com.cloud.computing.filesharingapp.dto;

/**
 * Enum representing different levels of password strength
 */
public enum PasswordStrength {
    WEAK("Weak", 1),
    MEDIUM("Medium", 2), 
    STRONG("Strong", 3);
    
    private final String displayName;
    private final int level;
    
    PasswordStrength(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getLevel() {
        return level;
    }
}