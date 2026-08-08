package com.media_vault_service.Blob.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class CryptoUtils {
    private static final String ALGO = "AES";

    // Legacy hardcoded fallback string value
    private static final String RAW_KEY_STRING = "GhostSecretKey_2026_Secure!!";

    private static SecretKeySpec getValidKeySpec() {
        // ✅ FIXED: Enforce a strict 32-byte array block boundary allocation
        byte[] keyBytes = new byte[32];
        byte[] rawBytes = RAW_KEY_STRING.getBytes(StandardCharsets.UTF_8);

        // Copy raw data bytes into the 32-byte array structure cleanly
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, 32));

        return new SecretKeySpec(keyBytes, ALGO);
    }

    public static byte[] encrypt(byte[] data) throws Exception {
        SecretKeySpec spec = getValidKeySpec();
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, spec);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] data) throws Exception {
        SecretKeySpec spec = getValidKeySpec();
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, spec);
        return cipher.doFinal(data);
    }
}