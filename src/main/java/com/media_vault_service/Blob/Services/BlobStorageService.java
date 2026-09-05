package com.media_vault_service.Blob.Services;

import com.media_vault_service.Blob.Config.CryptoUtils;
import com.media_vault_service.Blob.ExceptionHandlers.AssetNotFoundException;
import com.media_vault_service.Blob.Models.MediaVault;
import com.media_vault_service.Blob.Repositories.MediaVaultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlobStorageService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final MediaVaultRepository mediaVaultRepository;

    private static final String VAULT_CACHE_PREFIX = "vault:media:";

    // CHANGED: Holds raw byte array to support HTTP Range requests for Video Streaming
    public record DecryptedPayload(byte[] data, String contentType) {}

    public boolean deleteMedia(String mediaId) {
        log.info("  Executing database purge protocol for media payload: {}", mediaId);
        boolean isDeleted = false;
        try {
            Boolean cacheDeleted = redisTemplate.delete(VAULT_CACHE_PREFIX + mediaId);
            if (Boolean.TRUE.equals(cacheDeleted)) {
                log.info("  Vault Cache Wipe: Successfully eradicated from active memory array.");
            }

            if (mediaVaultRepository.existsById(mediaId)) {
                mediaVaultRepository.deleteById(mediaId);
                log.info("  Vault DB Wipe: Successfully destroyed encrypted record from database.");
                isDeleted = true;
            } else {
                log.warn("  Vault DB Wipe: Target footprint not found in database.");
                if (Boolean.TRUE.equals(cacheDeleted)) {
                    isDeleted = true;
                }
            }
        } catch (Exception e) {
            log.error("  Vault Purge Failure: {}", e.getMessage(), e);
        }
        return isDeleted;
    }

    public String saveMedia(MultipartFile file, String userId) throws Exception {
        String fileId = UUID.randomUUID().toString();
        byte[] rawBytes = file.getBytes();
        byte[] encryptedBytes = CryptoUtils.encrypt(rawBytes);

        MediaVault vaultRecord = MediaVault.builder()
                .id(fileId)
                .uploaderId(userId)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .encryptedData(encryptedBytes)
                .build();

        mediaVaultRepository.save(vaultRecord);
        log.info("  Vault DB Write: Encrypted payload permanently secured in database as {}", fileId);

        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                redisTemplate.opsForHash().put(VAULT_CACHE_PREFIX + fileId, "data", encryptedBytes);
                redisTemplate.opsForHash().put(VAULT_CACHE_PREFIX + fileId, "type", file.getContentType());
                redisTemplate.expire(VAULT_CACHE_PREFIX + fileId, 12, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("  Redis cache unavailable. Operating in DB-Only mode.");
        }
        return fileId;
    }

    public DecryptedPayload getDecryptedPayload(String mediaId) throws Exception {
        byte[] encryptedData = null;
        String contentType = "application/octet-stream";

        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                Object cachedBytes = redisTemplate.opsForHash().get(VAULT_CACHE_PREFIX + mediaId, "data");
                Object cachedType = redisTemplate.opsForHash().get(VAULT_CACHE_PREFIX + mediaId, "type");
                if (cachedBytes instanceof byte[]) {
                    encryptedData = (byte[]) cachedBytes;
                    if (cachedType != null) contentType = cachedType.toString();
                }
            }
        } catch (Exception e) {}

        if (encryptedData == null) {
            Optional<MediaVault> recordOptional = mediaVaultRepository.findById(mediaId);
            if (recordOptional.isPresent()) {
                encryptedData = recordOptional.get().getEncryptedData();
                contentType = recordOptional.get().getFileType();
                try {
                    if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                        redisTemplate.opsForHash().put(VAULT_CACHE_PREFIX + mediaId, "data", encryptedData);
                        redisTemplate.opsForHash().put(VAULT_CACHE_PREFIX + mediaId, "type", contentType);
                        redisTemplate.expire(VAULT_CACHE_PREFIX + mediaId, 12, TimeUnit.HOURS);
                    }
                } catch (Exception e) {}
            } else {
                throw new AssetNotFoundException("Asset not found in database with ID: " + mediaId);
            }
        }

        byte[] decryptedData = CryptoUtils.decrypt(encryptedData);
        // CHANGED: Pass byte[] directly instead of InputStream
        return new DecryptedPayload(decryptedData, contentType);
    }
}