package com.media_vault_service.Blob.Services;

import com.media_vault_service.Blob.Config.CryptoUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j

public class BlobStorageService {
    private final Path root = Paths.get("vault_data");

    public String saveMedia(MultipartFile file, String userId) throws Exception {
        // Hash the userId to create a hidden folder name
        String userFolder = DigestUtils.sha256Hex(userId).substring(0, 8);
        Path targetDir = root.resolve(userFolder);
        if (!Files.exists(targetDir)) Files.createDirectories(targetDir);

        String fileId = UUID.randomUUID().toString();
        Path filePath = targetDir.resolve(fileId + ".ghost");

        // Encrypting while writing to save RAM
        byte[] encrypted = CryptoUtils.encrypt(file.getBytes());
        Files.write(filePath, encrypted);

        return userFolder + "/" + fileId; // This "Ghost Path" goes to the DB
    }

    private byte[] encryptBytes(byte[] data) {
        // Simple XOR for speed; replace with AES for high production security
        byte[] key = "GHOST_KEY_2026".getBytes();
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }
    public InputStream getDecryptedStream(String ghostPath) throws Exception {
        Path path = root.resolve(ghostPath + ".ghost");
        byte[] encryptedData = Files.readAllBytes(path);
        byte[] decryptedData = CryptoUtils.decrypt(encryptedData);
        return new ByteArrayInputStream(decryptedData);
    }
}