package com.cloudshare.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class SecretsStartupValidator {

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

        log.info("All application secrets validated successfully.");
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
