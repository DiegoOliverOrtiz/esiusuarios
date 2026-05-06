package esi.edu.usuarios.usuarios.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class RiskDataEncryptionServiceTests {
    @Test
    void encryptsWithAesGcmAndDecryptsOriginalValue() {
        RiskDataEncryptionService service = new RiskDataEncryptionService(testKey());

        String first = service.encrypt("12345678Z");
        String second = service.encrypt("12345678Z");

        assertNotEquals("12345678Z", first);
        assertNotEquals(first, second);
        assertEquals("12345678Z", service.decrypt(first));
        assertEquals("12345678Z", service.decrypt(second));
    }

    @Test
    void refusesToEncryptRiskDataWithoutConfiguredKey() {
        RiskDataEncryptionService service = new RiskDataEncryptionService("");

        assertThrows(IllegalStateException.class, () -> service.encrypt("12345678Z"));
    }

    private String testKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
