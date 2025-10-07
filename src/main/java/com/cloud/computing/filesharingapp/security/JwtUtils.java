package com.cloud.computing.filesharingapp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Utility class for JWT (JSON Web Token) operations.
 * 
 * <p>This class provides functionality for:
 * <ul>
 *   <li>Generating JWT tokens for authenticated users</li>
 *   <li>Validating JWT tokens and handling various error conditions</li>
 *   <li>Extracting user information from JWT tokens</li>
 *   <li>Managing token expiration and security</li>
 * </ul>
 * 
 * <p>The class uses HMAC-SHA512 algorithm for token signing and includes
 * comprehensive error handling for various JWT-related exceptions.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${app.jwtSecret:mySecretKey}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    /**
     * Generates a JWT token for an authenticated user.
     * 
     * <p>The token includes:
     * <ul>
     *   <li>Subject: username of the authenticated user</li>
     *   <li>Issued At: current timestamp</li>
     *   <li>Expiration: current time + configured expiration period</li>
     *   <li>Signature: HMAC-SHA512 signature using the secret key</li>
     * </ul>
     * 
     * @param authentication the Spring Security authentication object
     * @return JWT token string
     */
    public String generateJwtToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        logger.debug("Generating JWT token for user: {}", userPrincipal.getUsername());

        String token = Jwts.builder()
                .setSubject((userPrincipal.getUsername()))
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS512)
                .compact();

        logger.debug("JWT token generated successfully for user: {}", userPrincipal.getUsername());
        return token;
    }

    /**
     * Creates the signing key from the configured JWT secret.
     * 
     * @return Key object for JWT signing and verification
     */
    private Key key() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extracts the username from a JWT token.
     * 
     * @param token the JWT token string
     * @return username stored in the token's subject claim
     */
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    /**
     * Validates a JWT token and checks for various error conditions.
     * 
     * <p>This method validates the token signature, expiration, and format.
     * It handles and logs specific JWT exceptions:
     * <ul>
     *   <li>MalformedJwtException: Invalid token format</li>
     *   <li>ExpiredJwtException: Token has expired</li>
     *   <li>UnsupportedJwtException: Unsupported token type</li>
     *   <li>IllegalArgumentException: Empty or null token</li>
     * </ul>
     * 
     * @param authToken the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }

        return false;
    }
}