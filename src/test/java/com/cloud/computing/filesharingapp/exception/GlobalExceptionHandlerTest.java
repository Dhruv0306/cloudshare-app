package com.cloud.computing.filesharingapp.exception;

import com.cloud.computing.filesharingapp.dto.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private WebRequest webRequest;

    @Mock
    private BindingResult bindingResult;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(webRequest, bindingResult);
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException and return validation errors")
    void handleValidationExceptions_ShouldReturnValidationErrors() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        FieldError fieldError1 = new FieldError("user", "email", "Email is required");
        FieldError fieldError2 = new FieldError("user", "password", "Password must be at least 8 characters");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Act
        ResponseEntity<ValidationErrorResponse> response = 
            globalExceptionHandler.handleValidationExceptions(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ValidationErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Validation failed", body.getMessage());
        assertEquals(2, body.getErrors().size());
        assertEquals("Email is required", body.getErrors().get("email"));
        assertEquals("Password must be at least 8 characters", body.getErrors().get("password"));
    }

    @Test
    @DisplayName("Should handle EmailVerificationException and return bad request")
    void handleEmailVerificationException_ShouldReturnBadRequest() {
        // Arrange
        String errorMessage = "Invalid verification code";
        EmailVerificationException exception = new EmailVerificationException(errorMessage);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleEmailVerificationException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + errorMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle RateLimitExceededException and return too many requests")
    void handleRateLimitExceededException_ShouldReturnTooManyRequests() {
        // Arrange
        String errorMessage = "Rate limit exceeded. Please try again later.";
        RateLimitExceededException exception = new RateLimitExceededException(errorMessage);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleRateLimitExceededException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + errorMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException and return bad request")
    void handlePasswordValidationException_ShouldReturnBadRequest() {
        // Arrange
        String errorMessage = "Password does not meet strength requirements";
        PasswordValidationException exception = new PasswordValidationException(errorMessage);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handlePasswordValidationException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + errorMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle RuntimeException and return internal server error")
    void handleRuntimeException_ShouldReturnInternalServerError() {
        // Arrange
        String errorMessage = "Unexpected runtime error";
        RuntimeException exception = new RuntimeException(errorMessage);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleRuntimeException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: An unexpected error occurred. Please try again.", body.getMessage());
    }

    @Test
    @DisplayName("Should rethrow AuthenticationException instead of handling it")
    void handleRuntimeException_ShouldRethrowAuthenticationException() {
        // Arrange
        AuthenticationException authException = mock(AuthenticationException.class);

        // Act & Assert
        assertThrows(AuthenticationException.class, () -> {
            globalExceptionHandler.handleRuntimeException(authException, webRequest);
        });
    }

    @Test
    @DisplayName("Should handle custom RuntimeException subclass normally")
    void handleRuntimeException_ShouldHandleCustomRuntimeException() {
        // Arrange
        class CustomRuntimeException extends RuntimeException {
            public CustomRuntimeException(String message) {
                super(message);
            }
        }
        
        CustomRuntimeException exception = new CustomRuntimeException("Custom error");

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleRuntimeException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: An unexpected error occurred. Please try again.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle generic Exception and return internal server error")
    void handleGlobalException_ShouldReturnInternalServerError() {
        // Arrange
        String errorMessage = "Unexpected exception";
        Exception exception = new Exception(errorMessage);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleGlobalException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: An unexpected error occurred. Please try again.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle null exception message gracefully")
    void handleRuntimeException_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        RuntimeException exception = new RuntimeException((String) null);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleRuntimeException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: An unexpected error occurred. Please try again.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle empty validation errors list")
    void handleValidationExceptions_WithEmptyErrors_ShouldReturnEmptyErrorMap() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of());

        // Act
        ResponseEntity<ValidationErrorResponse> response = 
            globalExceptionHandler.handleValidationExceptions(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ValidationErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Validation failed", body.getMessage());
        assertTrue(body.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Should handle multiple field errors for same field")
    void handleValidationExceptions_WithMultipleErrorsForSameField_ShouldUseLastError() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        FieldError fieldError1 = new FieldError("user", "email", "Email is required");
        FieldError fieldError2 = new FieldError("user", "email", "Email format is invalid");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Act
        ResponseEntity<ValidationErrorResponse> response = 
            globalExceptionHandler.handleValidationExceptions(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ValidationErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getErrors().size());
        assertEquals("Email format is invalid", body.getErrors().get("email"));
    }

    @Test
    @DisplayName("Should handle exception with cause")
    void handleRuntimeException_WithCause_ShouldHandleGracefully() {
        // Arrange
        Exception cause = new IllegalArgumentException("Root cause");
        RuntimeException exception = new RuntimeException("Runtime error", cause);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleRuntimeException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: An unexpected error occurred. Please try again.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle EmailVerificationException with null message")
    void handleEmailVerificationException_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        EmailVerificationException exception = new EmailVerificationException(null);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleEmailVerificationException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: null", body.getMessage());
    }

    @Test
    @DisplayName("Should handle RateLimitExceededException with empty message")
    void handleRateLimitExceededException_WithEmptyMessage_ShouldHandleGracefully() {
        // Arrange
        RateLimitExceededException exception = new RateLimitExceededException("");

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handleRateLimitExceededException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: ", body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException with cause")
    void handlePasswordValidationException_WithCause_ShouldHandleGracefully() {
        // Arrange
        Exception cause = new IllegalStateException("Invalid state");
        PasswordValidationException exception = new PasswordValidationException("Password error", cause);

        // Act
        ResponseEntity<MessageResponse> response = 
            globalExceptionHandler.handlePasswordValidationException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        MessageResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Error: Password error", body.getMessage());
    }
}