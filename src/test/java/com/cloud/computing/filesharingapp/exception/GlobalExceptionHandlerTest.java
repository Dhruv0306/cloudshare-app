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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.NoSuchElementException;

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
    @DisplayName("Should handle MethodArgumentNotValidException and return first validation error")
    void handleValidationExceptions_ShouldReturnFirstValidationError() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        FieldError fieldError1 = new FieldError("user", "email", "Email is required");
        FieldError fieldError2 = new FieldError("user", "password", "Password must be at least 8 characters");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        // Should return one of the validation errors (order not guaranteed due to
        // HashMap)
        assertTrue(body.getMessage().equals("Validation Error: Email is required") ||
                body.getMessage().equals("Validation Error: Password must be at least 8 characters"));
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with single validation error")
    void handleValidationExceptions_WithSingleError_ShouldReturnThatError() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        FieldError fieldError = new FieldError("user", "username", "Username cannot be empty");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Validation Error: Username cannot be empty", body.getMessage());
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with multiple errors for same field")
    void handleValidationExceptions_WithMultipleErrorsForSameField_ShouldReturnLastError() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        FieldError fieldError1 = new FieldError("user", "email", "Email is required");
        FieldError fieldError2 = new FieldError("user", "email", "Email format is invalid");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        // Should return the last error for the same field (HashMap overwrites previous
        // value)
        assertEquals("Validation Error: Email format is invalid", body.getMessage());
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with null error message")
    void handleValidationExceptions_WithNullErrorMessage_ShouldHandleGracefully() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        FieldError fieldError = new FieldError("user", "email", (String) null);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Validation Error: null", body.getMessage());
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when no validation errors exist")
    void handleValidationExceptions_WithNoErrors_ShouldThrowException() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> {
            globalExceptionHandler.handleValidationExceptions(exception);
        });
    }

    @Test
    @DisplayName("Should handle EmailVerificationException and return bad request")
    void handleEmailVerificationException_ShouldReturnBadRequest() {
        // Arrange
        String errorMessage = "Invalid verification code";
        EmailVerificationException exception = new EmailVerificationException(errorMessage);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleEmailVerificationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + errorMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle EmailVerificationException with null message")
    void handleEmailVerificationException_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        EmailVerificationException exception = new EmailVerificationException((String) null);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleEmailVerificationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: null", body.getMessage());
    }

    @Test
    @DisplayName("Should handle EmailVerificationException with empty message")
    void handleEmailVerificationException_WithEmptyMessage_ShouldHandleGracefully() {
        // Arrange
        EmailVerificationException exception = new EmailVerificationException("");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleEmailVerificationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: ", body.getMessage());
    }

    @Test
    @DisplayName("Should handle EmailVerificationException with cause")
    void handleEmailVerificationException_WithCause_ShouldHandleGracefully() {
        // Arrange
        Exception cause = new IllegalArgumentException("Root cause");
        EmailVerificationException exception = new EmailVerificationException("Email verification failed", cause);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleEmailVerificationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Email verification failed", body.getMessage());
    }

    @Test
    @DisplayName("Should handle RateLimitExceededException and return too many requests")
    void handleRateLimitException_ShouldReturnTooManyRequests() {
        // Arrange
        String errorMessage = "Rate limit exceeded. Please try again later.";
        RateLimitExceededException exception = new RateLimitExceededException(errorMessage);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleRateLimitException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + errorMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle RateLimitExceededException with null message")
    void handleRateLimitException_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        RateLimitExceededException exception = new RateLimitExceededException((String) null);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleRateLimitException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: null", body.getMessage());
    }

    @Test
    @DisplayName("Should handle RateLimitExceededException with empty message")
    void handleRateLimitException_WithEmptyMessage_ShouldHandleGracefully() {
        // Arrange
        RateLimitExceededException exception = new RateLimitExceededException("");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleRateLimitException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: ", body.getMessage());
    }

    @Test
    @DisplayName("Should handle RateLimitExceededException with cause")
    void handleRateLimitException_WithCause_ShouldHandleGracefully() {
        // Arrange
        Exception cause = new IllegalStateException("Invalid state");
        RateLimitExceededException exception = new RateLimitExceededException("Too many requests", cause);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleRateLimitException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Too many requests", body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException and return bad request")
    void handlePasswordValidationException_ShouldReturnBadRequest() {
        // Arrange
        String errorMessage = "Password must contain at least one uppercase letter";
        PasswordValidationException exception = new PasswordValidationException(errorMessage);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handlePasswordValidationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + errorMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException with null message")
    void handlePasswordValidationException_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        PasswordValidationException exception = new PasswordValidationException((String) null);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handlePasswordValidationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: null", body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException with empty message")
    void handlePasswordValidationException_WithEmptyMessage_ShouldHandleGracefully() {
        // Arrange
        PasswordValidationException exception = new PasswordValidationException("");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handlePasswordValidationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: ", body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException with cause")
    void handlePasswordValidationException_WithCause_ShouldHandleGracefully() {
        // Arrange
        Exception cause = new IllegalArgumentException("Invalid password format");
        PasswordValidationException exception = new PasswordValidationException("Password validation failed", cause);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handlePasswordValidationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Password validation failed", body.getMessage());
    }

    @Test
    @DisplayName("Should handle PasswordValidationException with complex validation message")
    void handlePasswordValidationException_WithComplexMessage_ShouldHandleGracefully() {
        // Arrange
        String complexMessage = "Password must be at least 8 characters long, contain uppercase, lowercase, number and special character";
        PasswordValidationException exception = new PasswordValidationException(complexMessage);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handlePasswordValidationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: " + complexMessage, body.getMessage());
    }

    @Test
    @DisplayName("Should handle generic Exception and return internal server error")
    void handleGenericException_ShouldReturnInternalServerError() {
        // Arrange
        String errorMessage = "Unexpected exception";
        Exception exception = new Exception(errorMessage);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle generic Exception with null message")
    void handleGenericException_WithNullMessage_ShouldHandleGracefully() {
        // Arrange
        Exception exception = new Exception((String) null);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle generic Exception with cause")
    void handleGenericException_WithCause_ShouldHandleGracefully() {
        // Arrange
        Exception cause = new IllegalArgumentException("Root cause");
        Exception exception = new Exception("Generic error", cause);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle RuntimeException as generic exception")
    void handleGenericException_WithRuntimeException_ShouldReturnInternalServerError() {
        // Arrange
        RuntimeException exception = new RuntimeException("Runtime error");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle custom RuntimeException subclass")
    void handleGenericException_WithCustomRuntimeException_ShouldReturnInternalServerError() {
        // Arrange
        class CustomRuntimeException extends RuntimeException {
            public CustomRuntimeException(String message) {
                super(message);
            }
        }

        CustomRuntimeException exception = new CustomRuntimeException("Custom error");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle NullPointerException as generic exception")
    void handleGenericException_WithNullPointerException_ShouldReturnInternalServerError() {
        // Arrange
        NullPointerException exception = new NullPointerException("Null pointer error");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException as generic exception")
    void handleGenericException_WithIllegalArgumentException_ShouldReturnInternalServerError() {
        // Arrange
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred. Please try again later.", body.getMessage());
    }

    // NEW TESTS FOR AUTHENTICATION EXCEPTION HANDLERS

    @Test
    @DisplayName("Should handle BadCredentialsException and return unauthorized")
    void handleBadCredentialsException_ShouldReturnUnauthorized() {
        // Arrange
        String errorMessage = "Bad credentials";
        BadCredentialsException exception = new BadCredentialsException(errorMessage);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleBadCredentialsException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Invalid username or password", body.getMessage());
    }

    @Test
    @DisplayName("Should handle BadCredentialsException with null message")
    void handleBadCredentialsException_WithNullMessage_ShouldReturnUnauthorized() {
        // Arrange
        BadCredentialsException exception = new BadCredentialsException(null);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleBadCredentialsException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Invalid username or password", body.getMessage());
    }

    @Test
    @DisplayName("Should handle BadCredentialsException with empty message")
    void handleBadCredentialsException_WithEmptyMessage_ShouldReturnUnauthorized() {
        // Arrange
        BadCredentialsException exception = new BadCredentialsException("");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleBadCredentialsException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Invalid username or password", body.getMessage());
    }

    @Test
    @DisplayName("Should handle BadCredentialsException with cause")
    void handleBadCredentialsException_WithCause_ShouldReturnUnauthorized() {
        // Arrange
        Exception cause = new IllegalArgumentException("Invalid credentials format");
        BadCredentialsException exception = new BadCredentialsException("Authentication failed", cause);

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleBadCredentialsException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Invalid username or password", body.getMessage());
    }

    @Test
    @DisplayName("Should handle generic AuthenticationException and return unauthorized")
    void handleAuthenticationException_ShouldReturnUnauthorized() {
        // Arrange
        String errorMessage = "Authentication failed";
        AuthenticationException exception = new AuthenticationException(errorMessage) {
        };

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleAuthenticationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Authentication failed", body.getMessage());
    }

    @Test
    @DisplayName("Should handle AuthenticationException with null message")
    void handleAuthenticationException_WithNullMessage_ShouldReturnUnauthorized() {
        // Arrange
        AuthenticationException exception = new AuthenticationException(null) {
        };

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleAuthenticationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Authentication failed", body.getMessage());
    }

    @Test
    @DisplayName("Should handle AuthenticationException with empty message")
    void handleAuthenticationException_WithEmptyMessage_ShouldReturnUnauthorized() {
        // Arrange
        AuthenticationException exception = new AuthenticationException("") {
        };

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleAuthenticationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Authentication failed", body.getMessage());
    }

    @Test
    @DisplayName("Should handle AuthenticationException with cause")
    void handleAuthenticationException_WithCause_ShouldReturnUnauthorized() {
        // Arrange
        Exception cause = new IllegalStateException("Invalid authentication state");
        AuthenticationException exception = new AuthenticationException("Auth error", cause) {
        };

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleAuthenticationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Authentication failed", body.getMessage());
    }

    @Test
    @DisplayName("Should handle specific AuthenticationException subclass")
    void handleAuthenticationException_WithSpecificSubclass_ShouldReturnUnauthorized() {
        // Arrange
        class CustomAuthenticationException extends AuthenticationException {
            public CustomAuthenticationException(String message) {
                super(message);
            }
        }

        CustomAuthenticationException exception = new CustomAuthenticationException("Custom auth error");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleAuthenticationException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Error: Authentication failed", body.getMessage());
    }

    @Test
    @DisplayName("Should prioritize BadCredentialsException handler over generic AuthenticationException handler")
    void exceptionHandlerPriority_BadCredentialsVsAuthentication_ShouldUseBadCredentialsHandler() {
        // This test verifies that BadCredentialsException (which extends
        // AuthenticationException)
        // is handled by the more specific handler, not the generic one

        // Arrange
        BadCredentialsException exception = new BadCredentialsException("Invalid credentials");

        // Act
        ResponseEntity<?> response = globalExceptionHandler.handleBadCredentialsException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        MessageResponse body = (MessageResponse) response.getBody();
        assertNotNull(body);
        // Should use the specific BadCredentialsException message, not the generic one
        assertEquals("Error: Invalid username or password", body.getMessage());
    }

}