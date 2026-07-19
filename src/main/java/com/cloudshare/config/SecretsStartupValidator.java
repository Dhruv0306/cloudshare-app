package com.cloudshare.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Component
@Slf4j
public class SecretsStartupValidator {

    @Autowired
    private CryptoProperties cryptoProperties;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Value("${storage.minio.access-key:}")
    private String minioAccessKey;

    @Value("${storage.minio.secret-key:}")
    private String minioSecretKey;

    @Value("${crypto.master-kek:}")
    private String masterKek;

    private static final Set<String> FORMER_DEFAULTS = Set.of(
            "StrongDBPassword123!",
            "32ByteSecretKeyForHMACSHA256SignatureAuthenticationOfCloudShareTokens",
            "minioadmin",
            "your_crypto_master_kek_32_bytes"
    );

    @PostConstruct
    public void validateSecrets() {
        log.info("Validating application secrets on startup...");

        validateSecret("spring.datasource.password", dbPassword, 8);
        validateSecret("security.jwt.secret", jwtSecret, 64);
        validateSecret("storage.minio.access-key", minioAccessKey, 5);
        validateSecret("storage.minio.secret-key", minioSecretKey, 8);
        validateSecret("crypto.master-kek", masterKek, 32);

        validateKekShape();

        log.info("All application secrets validated successfully.");
    }

    private void validateKekShape() {
        if (cryptoProperties == null) {
            return;
        }

        boolean allowRaw = cryptoProperties.getKek() != null && cryptoProperties.getKek().isAllowRawPassphrase();

        // Validate master KEK if it is configured and not already overridden by version 1 in keks map
        String masterKekVal = cryptoProperties.getMasterKek();
        if (masterKekVal != null && !masterKekVal.trim().isEmpty() && !cryptoProperties.getKeks().containsKey(1)) {
            checkKekShape("master KEK", masterKekVal, allowRaw);
        }

        // Validate all versioned KEKs in the map
        cryptoProperties.getKeks().forEach((version, kekVal) -> {
            if (kekVal != null && !kekVal.trim().isEmpty()) {
                checkKekShape("version " + version, kekVal, allowRaw);
            }
        });
    }

    private void checkKekShape(String versionLabel, String kekStr, boolean allowRaw) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(kekStr.trim());
        } catch (IllegalArgumentException e) {
            keyBytes = kekStr.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length != 32) {
            if (!allowRaw) {
                throw new IllegalStateException(String.format(
                        "KEK for %s is not exactly 32 bytes after Base64 decoding. " +
                        "If you intend to use a raw passphrase and digest it, set crypto.kek.allow-raw-passphrase=true.",
                        versionLabel));
            } else {
                log.warn("KEK for {} is not exactly 32 bytes after Base64 decoding. " +
                        "It will be digested using SHA-256 fallback because crypto.kek.allow-raw-passphrase=true.",
                        versionLabel);
            }
        }
    }

    private void validateSecret(String name, String value, int minLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(String.format("Secret '%s' is required but is blank.", name));
        }

        String trimmed = value.trim();

        if (FORMER_DEFAULTS.contains(trimmed)) {
            throw new IllegalStateException(String.format(
                    "Secret '%s' is using a known insecure former default value. Please change this value.", name));
        }

        if (trimmed.length() < minLength) {
            throw new IllegalStateException(String.format(
                    "Secret '%s' is too short (length: %d, required: at least %d).",
                    name, trimmed.length(), minLength));
        }
    }
}
