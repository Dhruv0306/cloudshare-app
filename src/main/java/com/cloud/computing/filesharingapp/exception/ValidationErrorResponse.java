package com.cloud.computing.filesharingapp.exception;

import java.util.Map;

/**
 * Response object for validation errors
 */
public class ValidationErrorResponse {
    private String message;
    private Map<String, String> errors;
    
    public ValidationErrorResponse() {}
    
    public ValidationErrorResponse(String message, Map<String, String> errors) {
        this.message = message;
        this.errors = errors;
    }
    
    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Map<String, String> getErrors() { return errors; }
    public void setErrors(Map<String, String> errors) { this.errors = errors; }
}