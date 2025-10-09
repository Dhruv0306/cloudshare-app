package com.cloud.computing.filesharingapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Security configuration specifically for file sharing endpoints.
 * 
 * <p>
 * This configuration class provides enhanced security settings for public
 * file sharing endpoints, including:
 * <ul>
 * <li>HTTPS enforcement for share links</li>
 * <li>Security headers to prevent XSS and clickjacking</li>
 * <li>CORS configuration for public share access</li>
 * <li>Rate limiting configuration for abuse prevention</li>
 * </ul>
 * 
 * <p>
 * The configuration is designed to work alongside the main WebSecurityConfig
 * to provide additional security layers specifically for file sharing
 * operations.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class ShareSecurityConfig {

    @Value("${app.sharing.require-https:true}")
    private boolean requireHttps;

    @Value("${app.sharing.max-access-per-ip-per-hour:100}")
    private int maxAccessPerIpPerHour;

    @Value("${app.sharing.suspicious-activity-threshold:50}")
    private int suspiciousActivityThreshold;

    /**
     * Configures CORS settings specifically for public share endpoints.
     * 
     * <p>
     * This configuration allows public access to share endpoints while
     * maintaining security through other means (token validation, rate limiting).
     * 
     * @return WebMvcConfigurer with CORS settings for share endpoints
     */
    @Bean
    public WebMvcConfigurer shareEndpointCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                // Configure CORS for public share endpoints
                registry.addMapping("/api/files/shared/**")
                        .allowedOriginPatterns("*") // Allow all origins for public shares
                        .allowedMethods("GET", "HEAD", "OPTIONS") // Only allow safe methods
                        .allowedHeaders("Accept", "Content-Type", "User-Agent", "Referer")
                        .allowCredentials(false) // No credentials needed for public shares
                        .maxAge(3600); // Cache preflight requests for 1 hour
            }
        };
    }

    /**
     * Creates a bean for XSS protection header configuration.
     * 
     * @return XXssProtectionHeaderWriter configured for share endpoints
     */
    @Bean
    public XXssProtectionHeaderWriter xssProtectionHeaderWriter() {
        return new XXssProtectionHeaderWriter();
    }

    /**
     * Creates a bean for referrer policy header configuration.
     * 
     * @return ReferrerPolicyHeaderWriter configured to prevent referrer leakage
     */
    @Bean
    public ReferrerPolicyHeaderWriter referrerPolicyHeaderWriter() {
        return new ReferrerPolicyHeaderWriter(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER);
    }

    /**
     * Gets the HTTPS requirement setting.
     * 
     * @return true if HTTPS is required for share links, false otherwise
     */
    public boolean isHttpsRequired() {
        return requireHttps;
    }

    /**
     * Gets the maximum access attempts per IP per hour.
     * 
     * @return the maximum access attempts allowed per IP per hour
     */
    public int getMaxAccessPerIpPerHour() {
        return maxAccessPerIpPerHour;
    }

    /**
     * Gets the threshold for detecting suspicious activity.
     * 
     * @return the number of accesses per hour that triggers suspicious activity
     *         detection
     */
    public int getSuspiciousActivityThreshold() {
        return suspiciousActivityThreshold;
    }
}