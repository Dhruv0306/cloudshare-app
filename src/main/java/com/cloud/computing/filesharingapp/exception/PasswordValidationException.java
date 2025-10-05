package com.cloud.computing.filesharingapp.exception;

/**
 * Exception thrown when password validation fails
 */
public class PasswordValidationException extends RuntimeException {
    
    public PasswordValidationException(String message) {
        super(message);
    }
    
    public PasswordValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}