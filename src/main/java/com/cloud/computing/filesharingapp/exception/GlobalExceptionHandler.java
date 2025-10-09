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

/**
 * Global exception handler for the file sharing application.
 * 
 * <p>This class provides centralized exception handling across the entire application,
 * ensuring consistent error responses and proper logging. It handles various types
 * of exceptions including:
 * <ul>
 *   <li>Validation errors from request DTOs</li>
 *   <li>Custom application exceptions (email verification, rate limiting, etc.)</li>
 *   <li>Spring Security authentication exceptions</li>
 *   <li>Generic runtime exceptions</li>
 * </ul>
 * 
 * <p>All exceptions are logged appropriately and converted to user-friendly
 * error messages while avoiding exposure of sensitive system information.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors from request DTOs.
     * 
     * <p>This method processes validation failures from @Valid annotations
     * and returns the first validation error message to the client.
     * 
     * @param ex the validation exception containing field errors
     * @return ResponseEntity with validation error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.warn("Validation errors: {}", errors);
        
        // Return the first error message for simplicity
        String firstError = errors.values().iterator().next();
        return ResponseEntity.badRequest()
                .body(new MessageResponse("Validation Error: " + firstError));
    }

    /**
     * Handles email verification related exceptions.
     * 
     * @param ex the email verification exception
     * @return ResponseEntity with error message
     */
    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<?> handleEmailVerificationException(EmailVerificationException ex) {
        logger.warn("Email verification error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new MessageResponse("Error: " + ex.getMessage()));
    }

    /**
     * Handles rate limiting exceptions when users exceed allowed request limits.
     * 
     * @param ex the rate limit exception
     * @return ResponseEntity with 429 Too Many Requests status
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimitException(RateLimitExceededException ex) {
        logger.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponse("Error: " + ex.getMessage()));
    }

    /**
     * Handles password validation exceptions during registration or password changes.
     * 
     * @param ex the password validation exception
     * @return ResponseEntity with validation error message
     */
    @ExceptionHandler(PasswordValidationException.class)
    public ResponseEntity<?> handlePasswordValidationException(PasswordValidationException ex) {
        logger.warn("Password validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new MessageResponse("Error: " + ex.getMessage()));
    }

    /**
     * Handles authentication failures due to invalid credentials.
     * 
     * @param ex the bad credentials exception
     * @return ResponseEntity with 401 Unauthorized status
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentialsException(org.springframework.security.authentication.BadCredentialsException ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponse("Error: Invalid username or password"));
    }

    /**
     * Handles general Spring Security authentication exceptions.
     * 
     * @param ex the authentication exception
     * @return ResponseEntity with 401 Unauthorized status
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex) {
        logger.warn("Authentication error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponse("Error: Authentication failed"));
    }

    /**
     * Handles Spring Security authorization exceptions (access denied).
     * 
     * @param ex the authorization exception
     * @return ResponseEntity with 403 Forbidden status
     */
    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAuthorizationDeniedException(org.springframework.security.authorization.AuthorizationDeniedException ex) {
        logger.warn("Authorization denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new MessageResponse("Error: Access denied"));
    }

    /**
     * Handles all other unexpected exceptions as a fallback.
     * 
     * <p>This method ensures that no unhandled exceptions leak sensitive
     * information to clients while providing proper error logging for debugging.
     * 
     * @param ex the unexpected exception
     * @param request the web request context
     * @return ResponseEntity with 500 Internal Server Error status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex, WebRequest request) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("An unexpected error occurred. Please try again later."));
    }
}