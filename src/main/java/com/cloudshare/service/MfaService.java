package com.cloudshare.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@Slf4j
public class MfaService {

    private final SecretGenerator secretGenerator;
    private final TimeProvider timeProvider;
    private final CodeGenerator codeGenerator;
    private final CodeVerifier codeVerifier;
    private final QrGenerator qrGenerator;
    private final StringRedisTemplate securityRedisTemplate;

    public MfaService(
            @Qualifier("securityRedisTemplate") StringRedisTemplate securityRedisTemplate) {
        this.securityRedisTemplate = securityRedisTemplate;
        this.secretGenerator = new DefaultSecretGenerator();
        this.timeProvider = new SystemTimeProvider();
        this.codeGenerator = new DefaultCodeGenerator();
        this.codeVerifier = new DefaultCodeVerifier(this.codeGenerator, this.timeProvider);
        this.qrGenerator = new ZxingPngQrGenerator();
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String generateQrCodeUri(String username, String secret) {
        try {
            QrData data = new QrData.Builder()
                    .label(username)
                    .secret(secret)
                    .issuer("CloudShare")
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();

            byte[] qrBytes = qrGenerator.generate(data);
            return "data:" + qrGenerator.getImageMimeType() + ";base64," + Base64.getEncoder().encodeToString(qrBytes);
        } catch (Exception e) {
            log.error("Failed to generate QR code URI for user {}", username, e);
            throw new RuntimeException("Could not generate MFA QR Code", e);
        }
    }

    public boolean verifyCode(String userId, String secret, String code) {
        if (secret == null || code == null || userId == null) {
            return false;
        }
        try {
            String trimmedCode = code.trim();
            if (!codeVerifier.isValidCode(secret, trimmedCode)) {
                return false;
            }
            long timeStep = timeProvider.getTime() / 30;
            String usedKey = "mfa:used:" + userId + ":" + timeStep;

            Boolean firstUse;
            try {
                firstUse = securityRedisTemplate.opsForValue()
                        .setIfAbsent(usedKey, "1", java.time.Duration.ofSeconds(90));
            } catch (Exception e) {
                log.error("MFA anti-replay check unavailable — failing closed for user {}", userId, e);
                return false;
            }

            if (!Boolean.TRUE.equals(firstUse)) {
                log.warn("Rejected replayed TOTP code for user {} at time-step {}", userId, timeStep);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Error verifying MFA code", e);
            return false;
        }
    }
}
