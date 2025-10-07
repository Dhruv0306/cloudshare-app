package com.cloud.computing.filesharingapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter for JWT token authentication and authorization.
 * 
 * <p>This filter intercepts HTTP requests to extract and validate JWT tokens
 * from the Authorization header. When a valid token is found, it:
 * <ul>
 *   <li>Extracts the username from the JWT token</li>
 *   <li>Loads user details from the database</li>
 *   <li>Creates an authentication context for the request</li>
 *   <li>Sets the security context for downstream processing</li>
 * </ul>
 * 
 * <p>The filter expects JWT tokens in the Authorization header with the format:
 * "Bearer {token}". Invalid or missing tokens result in unauthenticated requests
 * that may be handled by other security components.
 * 
 * <p>This filter runs once per request and integrates with Spring Security's
 * authentication framework to provide seamless JWT-based authentication.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class AuthTokenFilter extends OncePerRequestFilter {
    /** JWT utility service for token validation and parsing */
    @Autowired
    private JwtUtils jwtUtils;

    /** User details service for loading user information from database */
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /** Logger for authentication events and error tracking */
    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    /**
     * Processes each HTTP request to extract and validate JWT tokens.
     * 
     * <p>This method performs the following authentication steps:
     * <ol>
     *   <li>Extracts JWT token from the Authorization header</li>
     *   <li>Validates the token signature and expiration</li>
     *   <li>Extracts username from the validated token</li>
     *   <li>Loads user details from the database</li>
     *   <li>Creates and sets the security context for the request</li>
     * </ol>
     * 
     * <p>If any step fails (invalid token, user not found, etc.), the request
     * continues without authentication, allowing other security components to
     * handle authorization decisions.
     * 
     * @param request the HTTP servlet request
     * @param response the HTTP servlet response
     * @param filterChain the filter chain for continuing request processing
     * @throws ServletException if servlet processing fails
     * @throws IOException if I/O operations fail
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Extract JWT token from request header
            String jwt = parseJwt(request);
            
            // Validate token and set authentication if valid
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // Extract username from validated token
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                // Load user details from database
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                // Create authentication token with user details and authorities
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                
                // Set additional authentication details from the request
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set the authentication in the security context
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Log authentication errors but don't block the request
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Continue with the filter chain regardless of authentication status
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the Authorization header.
     * 
     * <p>This method looks for the "Authorization" header in the HTTP request
     * and extracts the JWT token if it follows the Bearer token format:
     * "Bearer {token}". The "Bearer " prefix is removed to return only the
     * token string.
     * 
     * @param request the HTTP servlet request containing headers
     * @return JWT token string if found and properly formatted, null otherwise
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        // Check if Authorization header exists and starts with "Bearer "
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            // Extract token by removing "Bearer " prefix (7 characters)
            return headerAuth.substring(7);
        }

        // No valid Authorization header found
        return null;
    }
}