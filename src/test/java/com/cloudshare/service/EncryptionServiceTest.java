package com.cloudshare.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private static final String TEST_KEK = "32ByteSecretKeyForHMACSHA256SignatureAuthenticationOfCloudShareTokens";

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "masterKekStr", TEST_KEK);
    }

    @Test
    void fekGeneration_andWrapping_success() throws Exception {
        // Generate FEK
        SecretKey originalFek = encryptionService.generateFek();
        assertNotNull(originalFek);
        assertEquals("AES", originalFek.getAlgorithm());
        assertEquals(32, originalFek.getEncoded().length); // 256 bits

        // Wrap the FEK using Master KEK
        String wrappedFek = encryptionService.wrapFek(originalFek);
        assertNotNull(wrappedFek);
        assertNotEquals(Base64.getEncoder().encodeToString(originalFek.getEncoded()), wrappedFek);

        // Unwrap the FEK
        SecretKey unwrappedFek = encryptionService.unwrapFek(wrappedFek);
        assertNotNull(unwrappedFek);
        assertArrayEquals(originalFek.getEncoded(), unwrappedFek.getEncoded());
    }

    @Test
    void encryptAndDecryptStream_success() throws Exception {
        String originalText = "This is a very sensitive message containing corporate secrets.";
        byte[] originalBytes = originalText.getBytes(StandardCharsets.UTF_8);

        SecretKey fek = encryptionService.generateFek();
        byte[] iv = encryptionService.generateIv();

        // Encrypt stream
        ByteArrayInputStream plaintextIn = new ByteArrayInputStream(originalBytes);
        ByteArrayOutputStream encryptedOut = new ByteArrayOutputStream();
        encryptionService.encryptStream(plaintextIn, encryptedOut, fek, iv);

        byte[] encryptedBytes = encryptedOut.toByteArray();
        assertNotNull(encryptedBytes);
        assertTrue(encryptedBytes.length > 0);
        assertNotEquals(originalText, new String(encryptedBytes, StandardCharsets.UTF_8));

        // Decrypt stream
        ByteArrayInputStream encryptedIn = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOut = new ByteArrayOutputStream();
        encryptionService.decryptStreamFully(encryptedIn, decryptedOut, fek, iv);

        String decryptedText = decryptedOut.toString(StandardCharsets.UTF_8);
        assertEquals(originalText, decryptedText);
    }
}
