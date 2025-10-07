package com.cloud.computing.filesharingapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication Entry Point for handling unauthorized access attempts.
 * 
 * <p>This component implements Spring Security's AuthenticationEntryPoint interface
 * to provide custom handling of authentication failures. When a user attempts to
 * access a protected resource without proper authentication, this entry point:
 * <ul>
 *   <li>Returns HTTP 401 (Unauthorized) status</li>
 *   <li>Provides structured JSON error response</li>
 *   <li>Logs authentication failures for security monitoring</li>
 *   <li>Includes request path information for debugging</li>
 * </ul>
 * 
 * <p>The structured error response helps client applications handle authentication
 * failures gracefully and provides consistent error formatting across the API.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {
    
    /** Logger for tracking authentication failures and security events */
    private static final Logger logger = LoggerFactory.getLogger(AuthEntryPointJwt.class);
    
    /**
     * Handles authentication failures by returning a structured error response.
     * 
     * <p>This method is invoked by Spring Security when an authentication attempt
     * fails or when an unauthenticated user tries to access a protected resource.
     * It creates a JSON response with error details and logs the failure for
     * security monitoring purposes.
     * 
     * <p>The response includes:
     * <ul>
     *   <li>HTTP status code (401)</li>
     *   <li>Error type ("Unauthorized")</li>
     *   <li>Detailed error message from the exception</li>
     *   <li>Request path that was attempted</li>
     * </ul>
     * 
     * @param request the HTTP servlet request that resulted in authentication failure
     * @param response the HTTP servlet response to write the error to
     * @param authException the authentication exception that occurred
     * @throws IOException if response writing fails
     * @throws ServletException if servlet processing fails
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // Log the authentication failure for security monitoring
        logger.error("Unauthorized error: {}", authException.getMessage());
        
        // Set response content type and status
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        // Create structured error response body
        final Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", authException.getMessage());
        body.put("path", request.getServletPath());
        
        // Write JSON response
        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}