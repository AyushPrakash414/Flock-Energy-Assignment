package com.flockenergy.meterops.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignerServiceTest {

    private final HmacSignerService signer = new HmacSignerService();

    @Test
    void sign_producesValidHexSignature() {
        var result = signer.sign("GET", "/portal/export", "page=1", "test-secret");

        assertNotNull(result.timestamp());
        assertNotNull(result.signature());
        // HMAC-SHA256 produces a 64-char hex string
        assertEquals(64, result.signature().length());
        // Signature should be valid hex
        assertTrue(result.signature().matches("[0-9a-f]+"));
    }

    @Test
    void sign_differentSecretsProduceDifferentSignatures() {
        var r1 = signer.sign("GET", "/portal/export", "page=1", "secret-1");
        var r2 = signer.sign("GET", "/portal/export", "page=1", "secret-2");

        assertNotEquals(r1.signature(), r2.signature());
    }
}
