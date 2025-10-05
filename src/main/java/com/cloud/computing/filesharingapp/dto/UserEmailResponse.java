package com.cloud.computing.filesharingapp.dto;

public class UserEmailResponse {
    private String email;
    
    public UserEmailResponse() {}
    
    public UserEmailResponse(String email) {
        this.email = email;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
}