package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EmailVerificationFilter extends OncePerRequestFilter {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        // Skip verification for auth endpoints and public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() && 
            authentication.getPrincipal() instanceof UserPrincipal) {
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            // Check if user's email is verified and account is active
            if (!userPrincipal.isEmailVerified() || userPrincipal.getAccountStatus() != AccountStatus.ACTIVE) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Email verification required");
                errorResponse.put("message", "Please verify your email address to access this resource");
                errorResponse.put("emailVerified", userPrincipal.isEmailVerified());
                errorResponse.put("accountStatus", userPrincipal.getAccountStatus().toString());
                
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/") || 
               path.startsWith("/api/test/") || 
               path.startsWith("/h2-console/") ||
               path.equals("/api/auth/verify-email") ||
               path.equals("/api/auth/resend-verification") ||
               path.equals("/api/auth/check-password-strength");
    }
}