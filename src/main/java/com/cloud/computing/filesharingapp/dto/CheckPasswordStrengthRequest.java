package com.cloud.computing.filesharingapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CheckPasswordStrengthRequest {
    @NotBlank(message = "Password is required")
    @Size(max = 100, message = "Password must not exceed 100 characters")
    private String password;
    
    // Constructors
    public CheckPasswordStrengthRequest() {}
    
    public CheckPasswordStrengthRequest(String password) {
        this.password = password;
    }
    
    // Getters and Setters
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}