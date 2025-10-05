package com.cloud.computing.filesharingapp.exception;

/**
 * Exception thrown when rate limits are exceeded
 */
public class RateLimitExceededException extends RuntimeException {
    
    public RateLimitExceededException(String message) {
        super(message);
    }
    
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}