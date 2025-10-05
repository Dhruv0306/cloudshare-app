package com.cloud.computing.filesharingapp.exception;

import com.cloud.computing.filesharingapp.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        logger.warn("Validation error occurred: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
            "Validation failed", 
            errors
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle custom business logic exceptions
     */
    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<MessageResponse> handleEmailVerificationException(
            EmailVerificationException ex, WebRequest request) {
        
        logger.warn("Email verification error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new MessageResponse("Error: " + ex.getMessage()));
    }
    
    /**
     * Handle rate limiting exceptions
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<MessageResponse> handleRateLimitExceededException(
            RateLimitExceededException ex, WebRequest request) {
        
        logger.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponse("Error: " + ex.getMessage()));
    }
    
    /**
     * Handle password validation exceptions
     */
    @ExceptionHandler(PasswordValidationException.class)
    public ResponseEntity<MessageResponse> handlePasswordValidationException(
            PasswordValidationException ex, WebRequest request) {
        
        logger.warn("Password validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new MessageResponse("Error: " + ex.getMessage()));
    }
    
    /**
     * Handle generic runtime exceptions (excluding Spring Security exceptions)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<MessageResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        // Let Spring Security handle authentication exceptions
        if (ex instanceof org.springframework.security.core.AuthenticationException) {
            throw ex;
        }
        
        logger.error("Runtime exception occurred: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("Error: An unexpected error occurred. Please try again."));
    }
    
    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        
        logger.error("Unexpected exception occurred: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("Error: An unexpected error occurred. Please try again."));
    }
}