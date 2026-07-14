package com.cloudshare.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class SecretsStartupValidatorTest {

    private SecretsStartupValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SecretsStartupValidator();
        // Set default valid fields
        ReflectionTestUtils.setField(validator, "dbPassword", "StrongTestDBPassword123!");
        ReflectionTestUtils.setField(validator, "jwtSecret", "AValid64ByteLongDummyJWTSecretKeyForTestsThatIsAtLeast512BitsLong");
        ReflectionTestUtils.setField(validator, "minioAccessKey", "testminioaccesskey");
        ReflectionTestUtils.setField(validator, "minioSecretKey", "testminiosecretkey");
        ReflectionTestUtils.setField(validator, "masterKek", "test_crypto_master_kek_32_bytes_x");
    }

    @Test
    void validateSecrets_success() {
        assertDoesNotThrow(() -> validator.validateSecrets());
    }

    @Test
    void validateSecrets_blankDbPassword_throwsException() {
        ReflectionTestUtils.setField(validator, "dbPassword", "   ");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void validateSecrets_defaultDbPassword_throwsException() {
        ReflectionTestUtils.setField(validator, "dbPassword", "StrongDBPassword123!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertTrue(ex.getMessage().contains("known insecure former default"));
    }

    @Test
    void validateSecrets_shortDbPassword_throwsException() {
        ReflectionTestUtils.setField(validator, "dbPassword", "short");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    void validateSecrets_defaultJwtSecret_throwsException() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "32ByteSecretKeyForHMACSHA256SignatureAuthenticationOfCloudShareTokens");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("security.jwt.secret"));
        assertTrue(ex.getMessage().contains("known insecure former default"));
    }

    @Test
    void validateSecrets_shortJwtSecret_throwsException() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "short-jwt-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("security.jwt.secret"));
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    void validateSecrets_defaultMinioAccessKey_throwsException() {
        ReflectionTestUtils.setField(validator, "minioAccessKey", "minioadmin");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("storage.minio.access-key"));
        assertTrue(ex.getMessage().contains("known insecure former default"));
    }

    @Test
    void validateSecrets_defaultMasterKek_throwsException() {
        ReflectionTestUtils.setField(validator, "masterKek", "your_crypto_master_kek_32_bytes");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateSecrets());
        assertTrue(ex.getMessage().contains("crypto.master-kek"));
        assertTrue(ex.getMessage().contains("known insecure former default"));
    }
}
