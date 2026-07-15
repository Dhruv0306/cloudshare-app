package com.cloudshare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenService {

    private final StringRedisTemplate securityRedisTemplate;
    private final long refreshExpirationSeconds;

    public RefreshTokenService(
            @Qualifier("securityRedisTemplate") StringRedisTemplate securityRedisTemplate,
            @Value("${security.jwt.refresh-expiration-seconds:604800}") long refreshExpirationSeconds) {
        this.securityRedisTemplate = securityRedisTemplate;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    /**
     * Creates a new refresh token for a user.
     */
    public String createRefreshToken(UUID userId) {
        String tokenId = UUID.randomUUID().toString();
        String activeKey = "refresh:active:" + tokenId;
        String metadataKey = "refresh:metadata:" + tokenId;
        String familyKey = "refresh:family:" + userId;

        Duration ttl = Duration.ofSeconds(refreshExpirationSeconds);

        // Store active token mapped to user ID
        securityRedisTemplate.opsForValue().set(activeKey, userId.toString(), ttl);
        // Store metadata mapping to user ID (persists after rotation to detect reuse)
        securityRedisTemplate.opsForValue().set(metadataKey, userId.toString(), ttl);
        // Add to the user's token family set
        securityRedisTemplate.opsForSet().add(familyKey, tokenId);
        securityRedisTemplate.expire(familyKey, ttl);

        log.debug("Created refresh token {} for user {}", tokenId, userId);
        return tokenId;
    }

    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class TokenRotationResult {
        private final String newTokenId;
        private final UUID userId;
    }

    /**
     * Rotates an active refresh token, returning the new token and associated user ID.
     * Detects reuse and triggers family-wide revocation if a previously used token is presented.
     */
    public TokenRotationResult rotateRefreshToken(String oldTokenId) {
        String activeKey = "refresh:active:" + oldTokenId;
        String userIdStr = securityRedisTemplate.opsForValue().getAndDelete(activeKey);

        if (userIdStr != null) {
            UUID userId = UUID.fromString(userIdStr);
            log.debug("Valid refresh token rotation request for token {} (user {})", oldTokenId, userId);

            // Note: we keep oldTokenId in the family set and metadata to detect reuse

            // Generate new token
            String newTokenId = createRefreshToken(userId);
            return new TokenRotationResult(newTokenId, userId);
        }

        // Active token not found. Check if it's a reuse attempt.
        String metadataKey = "refresh:metadata:" + oldTokenId;
        String historicUserIdStr = securityRedisTemplate.opsForValue().get(metadataKey);

        if (historicUserIdStr != null) {
            UUID userId = UUID.fromString(historicUserIdStr);
            String familyKey = "refresh:family:" + userId;
            
            // Check if this token was part of the user's family
            Boolean isMember = securityRedisTemplate.opsForSet().isMember(familyKey, oldTokenId);
            if (Boolean.TRUE.equals(isMember)) {
                log.warn("DETECTED REUSE OF ROTATED REFRESH TOKEN {}! Revoking entire family for user {}", oldTokenId, userId);
                revokeAllUserTokens(userId);
                throw new SecurityException("MFA/Session breach detected. Force re-authentication.");
            }
        }

        throw new IllegalArgumentException("Invalid or expired refresh token.");
    }

    /**
     * Revokes all active refresh tokens in a user's family.
     */
    public void revokeAllUserTokens(UUID userId) {
        String familyKey = "refresh:family:" + userId;
        Set<String> tokenIds = securityRedisTemplate.opsForSet().members(familyKey);

        if (tokenIds != null && !tokenIds.isEmpty()) {
            for (String tokenId : tokenIds) {
                securityRedisTemplate.delete("refresh:active:" + tokenId);
                securityRedisTemplate.delete("refresh:metadata:" + tokenId);
            }
        }
        securityRedisTemplate.delete(familyKey);
        log.info("Revoked all refresh tokens for user {}", userId);
    }

    /**
     * Revokes a single token (e.g. during logout).
     */
    public void revokeToken(String tokenId) {
        String activeKey = "refresh:active:" + tokenId;
        String userIdStr = securityRedisTemplate.opsForValue().get(activeKey);
        
        if (userIdStr != null) {
            UUID userId = UUID.fromString(userIdStr);
            securityRedisTemplate.delete(activeKey);
            securityRedisTemplate.delete("refresh:metadata:" + tokenId);
            securityRedisTemplate.opsForSet().remove("refresh:family:" + userId, tokenId);
            log.debug("Revoked refresh token {}", tokenId);
        }
    }

    /**
     * Blacklists an access token by its JTI until its expiration.
     */
    public void blacklistAccessToken(String jti, long remainingSeconds) {
        if (jti != null && remainingSeconds > 0) {
            String blacklistKey = "blacklist:token:" + jti;
            securityRedisTemplate.opsForValue().set(blacklistKey, "true", Duration.ofSeconds(remainingSeconds));
            log.debug("Blacklisted access token jti={} for {} seconds", jti, remainingSeconds);
        }
    }
}
