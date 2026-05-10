package esi.edu.usuarios.usuarios.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RiskDataEncryptionService {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] key;

    public RiskDataEncryptionService(@Value("${app.risk-data.encryption-key:}") String encodedKey) {
        this.key = decodeKey(encodedKey);
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        if (key == null) {
            throw new IllegalStateException("Falta configurar app.risk-data.encryption-key para cifrar datos de riesgo.");
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato de riesgo.", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        if (key == null) {
            throw new IllegalStateException("Falta configurar app.risk-data.encryption-key para descifrar datos de riesgo.");
        }

        try {
            byte[] input = Base64.getUrlDecoder().decode(encryptedText);
            if (input.length <= IV_BYTES) {
                throw new IllegalArgumentException("Dato cifrado no válido.");
            }

            byte[] iv = Arrays.copyOfRange(input, 0, IV_BYTES);
            byte[] cipherText = Arrays.copyOfRange(input, IV_BYTES, input.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el dato de riesgo.", e);
        }
    }

    private byte[] decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }

        byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
        if (decoded.length != KEY_BYTES) {
            throw new IllegalArgumentException("La clave AES-256 debe tener 32 bytes codificados en Base64.");
        }
        return decoded;
    }
}
