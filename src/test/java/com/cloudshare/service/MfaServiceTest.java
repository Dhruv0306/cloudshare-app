package com.cloudshare.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MfaServiceTest {

    private MfaService mfaService;
    private CodeGenerator codeGenerator;
    private TimeProvider timeProvider;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService();
        codeGenerator = new DefaultCodeGenerator();
        timeProvider = new SystemTimeProvider();
    }

    @Test
    void testGenerateSecret() {
        String secret = mfaService.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length());
    }

    @Test
    void testGenerateQrCodeUri() {
        String secret = mfaService.generateSecret();
        String qrUri = mfaService.generateQrCodeUri("testuser", secret);
        assertNotNull(qrUri);
        assertTrue(qrUri.startsWith("data:image/png;base64,"));
    }

    @Test
    void testVerifyCodeSuccess() throws Exception {
        String secret = mfaService.generateSecret();
        long time = timeProvider.getTime();
        long counter = Math.floorDiv(time, 30);
        String correctCode = codeGenerator.generate(secret, counter);

        assertTrue(mfaService.verifyCode(secret, correctCode));
    }

    @Test
    void testVerifyCodeFailure() {
        String secret = mfaService.generateSecret();
        assertFalse(mfaService.verifyCode(secret, "000000"));
        assertFalse(mfaService.verifyCode(secret, "invalid"));
        assertFalse(mfaService.verifyCode(null, "123456"));
    }
}
