package com.media_vault_service.Blob.DataCompression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtil {

    /**
     * Compresses plain text into a compact Base64 string.
     * Shrinks text by up to 60-80% for medium/long posts.
     */
    public static String compress(String srcString) {
        if (srcString == null || srcString.isEmpty()) {
            return srcString;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {

            gzip.write(srcString.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            // Fallback to raw string if compression fails
            return srcString;
        }
    }

    /**
     * Decompresses a Base64 GZIP string back to plain text.
     */
    public static String decompress(String compressedBase64) {
        if (compressedBase64 == null || compressedBase64.isEmpty()) {
            return compressedBase64;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(compressedBase64);
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                 GZIPInputStream gzip = new GZIPInputStream(in);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[256];
                int len;
                while ((len = gzip.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // If it wasn't compressed, return original string safely
            return compressedBase64;
        }
    }
}