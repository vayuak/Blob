package com.media_vault_service.Blob.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private static final String ALGO = "AES";
    private static final byte[] KEY = "GhostSecretKey_2026_Secure!!".getBytes(); // 16/32 bytes

    public static byte[] encrypt(byte[] data) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(KEY, ALGO);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, spec);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] data) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(KEY, ALGO);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, spec);
        return cipher.doFinal(data);
    }
}
