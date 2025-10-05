package com.cloud.computing.filesharingapp.exception;

/**
 * Exception thrown when email verification operations fail
 */
public class EmailVerificationException extends RuntimeException {
    
    public EmailVerificationException(String message) {
        super(message);
    }
    
    public EmailVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}