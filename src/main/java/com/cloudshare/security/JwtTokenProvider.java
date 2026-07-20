package com.cloudshare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Service component responsible for generating, parsing, and validating JSON Web Tokens (JWTs).
 * <p>
 * <b>Why explicit token types exist:</b> To protect against JWT type-confusion attacks where a valid token
 * issued for one context (e.g., administrative step-up authentication or refresh tokens) is presented to an
 * endpoint expecting standard bearer access authorization. Every JWT issued includes an explicit {@code type}
 * claim ({@code "access"} vs {@code "step_up"}) and a unique cryptographic JTI (JWT ID) to enable precise
 * blacklisting and single-use enforcement.
 * </p>
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-seconds}")
    private long jwtExpirationSeconds;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a standard bearer access token for authenticated API requests.
     * <p>
     * <b>Why {@code type: "access"} is set:</b> Enforces that standard bearer tokens cannot be misused in
     * step-up authorization filters or refresh token endpoints.
     * </p>
     *
     * @param userId   the authenticated user's UUID string
     * @param username the authenticated user's username
     * @param roles    list of GrantedAuthority string representations
     * @return signed JWT access token string
     */
    public String generateAccessToken(String userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (jwtExpirationSeconds * 1000));

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("roles", roles);
        claims.put("type", "access");

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(userId)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Generates a short-lived MFA step-up authorization token required for sensitive administrative paths.
     * <p>
     * <b>Why 5-minute TTL & separate claims exist:</b> Client-side countdown timers are UX visual aids only.
     * The 5-minute server-side JWT expiration claim combined with single-use JTI Redis blacklisting forms the
     * true security boundary. The {@code step_up: true} and {@code type: "step_up"} claims ensure the token
     * is distinct from regular access tokens and cannot be substituted.
     * </p>
     *
     * @param userId   the administrative user's UUID string
     * @param username the administrative user's username
     * @return signed JWT step-up token string with 300-second expiration
     */
    public String generateStepUpToken(String userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (300 * 1000)); // 5 minutes

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("step_up", true);
        claims.put("type", "step_up");

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(userId)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Validates a step-up token against signature integrity, type claims, and expected user identity.
     * <p>
     * <b>Why user binding is checked:</b> Ensures step-up tokens issued to one administrator cannot be stolen
     * or replayed by another authenticated account to bypass MFA requirements.
     * </p>
     *
     * @param token          the step-up token string passed in {@code X-StepUp-Token} header
     * @param expectedUserId the UUID string of the current SecurityContext principal
     * @return {@code true} if valid, unexpired, bound to expectedUserId, and typed as "step_up"
     */
    public boolean validateStepUpToken(String token, String expectedUserId) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Boolean stepUp = claims.get("step_up", Boolean.class);
            String type = claims.get("type", String.class);
            String userId = claims.getSubject();
            return stepUp != null && stepUp && "step_up".equals(type) && userId != null
                    && userId.equals(expectedUserId);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid step-up token: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Extracts the explicit {@code type} claim from a JWT.
     *
     * @param token JWT string
     * @return type string (e.g. "access", "step_up") or {@code null} if invalid
     */
    public String getTokenType(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.get("type", String.class);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Extracts subject user ID from a valid JWT.
     *
     * @param token JWT string
     * @return user ID string
     */
    public String getUserIdFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("roles", List.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("username", String.class);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.getExpiration());
    }

    public String getJtiFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.getId());
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Resolves a JWT into an immutable {@link ResolvedJwt} summary record.
     * <p>
     * <b>Why this method exists:</b> Allows filters early in the filter chain (such as {@link RateLimitingFilter})
     * to parse and validate a token once, storing the resulting {@link ResolvedJwt} in request attributes so
     * downstream filters (such as {@link JwtAuthenticationFilter}) do not incur duplicate cryptographic overhead.
     * </p>
     *
     * @param token JWT string from request header
     * @return populated {@link ResolvedJwt} object
     */
    public ResolvedJwt resolveToken(String token) {
        if (!org.springframework.util.StringUtils.hasText(token)) {
            return new ResolvedJwt(token, false, null, null, null);
        }
        try {
            Claims claims = getAllClaimsFromToken(token);
            return new ResolvedJwt(
                    token,
                    true,
                    claims.getSubject(),
                    claims.get("type", String.class),
                    claims.getId());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            return new ResolvedJwt(token, false, null, null, null);
        }
    }

    /**
     * Validates signature and expiration of a JWT.
     *
     * @param authToken JWT string
     * @return {@code true} if signature is valid and token is unexpired
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }
}
