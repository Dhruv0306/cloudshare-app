package com.cloudshare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionService {

    @Value("${crypto.master-kek}")
    private String masterKekStr;

    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey getMasterKek() {
        byte[] keyBytes;
        try {
            // Attempt to decode KEK as Base64 first
            keyBytes = Base64.getDecoder().decode(masterKekStr.trim());
        } catch (IllegalArgumentException e) {
            // Fallback to raw UTF-8 bytes if not valid Base64
            keyBytes = masterKekStr.getBytes(StandardCharsets.UTF_8);
        }

        // If the key is not exactly 256 bits (32 bytes), digest it to enforce correctness
        if (keyBytes.length != 32) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                keyBytes = digest.digest(keyBytes);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 digest algorithm is unavailable", ex);
            }
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public SecretKey generateFek() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize AES KeyGenerator", e);
        }
    }

    public byte[] generateIv() {
        byte[] iv = new byte[12]; // 12 bytes standard GCM IV
        secureRandom.nextBytes(iv);
        return iv;
    }

    public String wrapFek(SecretKey fek) throws GeneralSecurityException {
        SecretKey masterKek = getMasterKek();
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.WRAP_MODE, masterKek);
        byte[] wrappedBytes = cipher.wrap(fek);
        return Base64.getEncoder().encodeToString(wrappedBytes);
    }

    public SecretKey unwrapFek(String wrappedFekBase64) throws GeneralSecurityException {
        SecretKey masterKek = getMasterKek();
        byte[] wrappedBytes = Base64.getDecoder().decode(wrappedFekBase64);
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.UNWRAP_MODE, masterKek);
        return (SecretKey) cipher.unwrap(wrappedBytes, "AES", Cipher.SECRET_KEY);
    }

    public void encryptStream(InputStream plaintextIn, OutputStream encryptedOut, SecretKey fek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, fek, parameterSpec);

        try (CipherOutputStream cos = new CipherOutputStream(encryptedOut, cipher)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = plaintextIn.read(buffer)) != -1) {
                cos.write(buffer, 0, read);
            }
            cos.flush();
        }
    }

    public InputStream decryptStream(InputStream encryptedIn, SecretKey fek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, fek, parameterSpec);
        return new CipherInputStream(encryptedIn, cipher);
    }
}
