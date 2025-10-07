package com.cloud.computing.filesharingapp.config;

import com.cloud.computing.filesharingapp.security.AuthEntryPointJwt;
import com.cloud.computing.filesharingapp.security.AuthTokenFilter;
import com.cloud.computing.filesharingapp.security.EmailVerificationFilter;
import com.cloud.computing.filesharingapp.security.RateLimitingFilter;
import com.cloud.computing.filesharingapp.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security configuration for the file sharing application.
 * 
 * <p>This configuration class sets up comprehensive security features including:
 * <ul>
 *   <li>JWT-based stateless authentication</li>
 *   <li>CORS configuration for frontend integration</li>
 *   <li>Rate limiting to prevent abuse</li>
 *   <li>Email verification enforcement</li>
 *   <li>Method-level security annotations</li>
 *   <li>Public endpoints for authentication and registration</li>
 * </ul>
 * 
 * <p>The security filter chain includes multiple custom filters:
 * <ol>
 *   <li>Rate Limiting Filter - prevents request flooding</li>
 *   <li>JWT Authentication Filter - validates JWT tokens</li>
 *   <li>Email Verification Filter - ensures email verification for protected endpoints</li>
 * </ol>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {
    @Autowired
    UserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;
    
    /**
     * Creates the JWT authentication filter bean.
     * 
     * @return AuthTokenFilter instance for JWT token validation
     */
    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }
    
    /**
     * Creates the email verification filter bean.
     * 
     * @return EmailVerificationFilter instance for enforcing email verification
     */
    @Bean
    public EmailVerificationFilter emailVerificationFilter() {
        return new EmailVerificationFilter();
    }
    
    /**
     * Creates the rate limiting filter bean.
     * 
     * @return RateLimitingFilter instance for request rate limiting
     */
    @Bean
    public RateLimitingFilter rateLimitingFilter() {
        return new RateLimitingFilter();
    }
    
    /**
     * Creates the authentication manager bean.
     * 
     * @param authConfig the authentication configuration
     * @return AuthenticationManager for handling authentication
     * @throws Exception if configuration fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    /**
     * Creates the password encoder bean using BCrypt.
     * 
     * @return PasswordEncoder for secure password hashing
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * Configures the main security filter chain.
     * 
     * <p>This method sets up:
     * <ul>
     *   <li>CORS configuration for cross-origin requests</li>
     *   <li>CSRF protection (disabled for stateless JWT authentication)</li>
     *   <li>Stateless session management</li>
     *   <li>Public endpoints for authentication and registration</li>
     *   <li>Custom security filters in the correct order</li>
     *   <li>H2 console access for development</li>
     * </ul>
     * 
     * @param http the HttpSecurity configuration object
     * @return SecurityFilterChain configured for the application
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> 
                auth.requestMatchers("/api/auth/signin").permitAll()
                    .requestMatchers("/api/auth/signup").permitAll()
                    .requestMatchers("/api/auth/verify-email").permitAll()
                    .requestMatchers("/api/auth/resend-verification").permitAll()
                    .requestMatchers("/api/auth/check-password-strength").permitAll()
                    .requestMatchers("/api/auth/user-email/**").permitAll()
                    .requestMatchers("/api/test/**").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .anyRequest().authenticated()
            );
        
        // Authentication provider is automatically configured
        http.addFilterBefore(rateLimitingFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(emailVerificationFilter(), AuthTokenFilter.class);
        
        // For H2 Console
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        
        return http.build();
    }
    
    /**
     * Configures CORS (Cross-Origin Resource Sharing) settings.
     * 
     * <p>This configuration allows the frontend application to make requests
     * to the backend API from different origins. It permits all origins,
     * methods, and headers with credentials support for development purposes.
     * 
     * <p><strong>Note:</strong> In production, the allowed origins should be
     * restricted to specific domains for security.
     * 
     * @return CorsConfigurationSource with CORS settings
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}