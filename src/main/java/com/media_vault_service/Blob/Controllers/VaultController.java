package com.media_vault_service.Blob.Controllers;

import com.media_vault_service.Blob.Services.BlobStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
public class VaultController {

    private final BlobStorageService blobService;
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024; // Strict 100MB threshold limit

    // 🟢 FIX 1: Enforce absolute public domain for React Native
    @Value("${blob.public.url:https://blob-production-d31a.up.railway.app}")
    private String publicBlobUrl;

    public String buildStreamUrl(String mediaId) {
        if (mediaId == null) return null;
        String baseUrl = publicBlobUrl.endsWith("/")
                ? publicBlobUrl.substring(0, publicBlobUrl.length() - 1)
                : publicBlobUrl;
        return baseUrl + "/api/vault/stream/" + mediaId;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        try {
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Map.of("error", "SIZE_VIOLATION", "message", "Multi-media tracking payloads are strictly capped at 100MB max."));
            }

            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("audio/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "FORMAT_PROHIBITED", "message", "Audio channel recording uploads are prohibited on this node framework."));
            }

            log.info("Vault executing encrypted write operations for user handle reference: {}", userId);
            String ghostPath = blobService.saveMedia(file, userId);

            // 🟢 FIX 2: Generate the unbreakable public HTTPS link
            String absoluteStreamUrl = buildStreamUrl(ghostPath);

            // 🟢 FIX 3: Return every single key the SocialController expects
            Map<String, Object> response = new HashMap<>();
            response.put("mediaId", ghostPath);
            response.put("mediaUrl", absoluteStreamUrl);
            response.put("mediaType", detectMediaType(contentType));
            response.put("avatarUrl", absoluteStreamUrl);
            response.put("profilePictureUrl", absoluteStreamUrl);
            response.put("status", "VAULTED");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stream/{mediaId}")
    public ResponseEntity<Resource> streamMedia(@PathVariable String mediaId) throws Exception {
        BlobStorageService.DecryptedPayload payload = blobService.getDecryptedPayload(mediaId);

        MediaType mediaType = MediaType.parseMediaType(
                payload.contentType() != null ? payload.contentType() : "application/octet-stream"
        );

        InputStream inputStream = payload.stream();
        long contentLength = inputStream.available();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                .contentType(mediaType)
                .contentLength(contentLength > 0 ? contentLength : -1)
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/delete/{mediaId}")
    public ResponseEntity<?> deleteMediaEndpoint(@PathVariable String mediaId) {
        try {
            log.info("🔒 Vault executing secure purge operations for media footprint: {}", mediaId);
            boolean deleted = blobService.deleteMedia(mediaId);

            if (deleted) {
                return ResponseEntity.ok(Map.of("status", "PURGED", "message", "Media deleted from storage volume."));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "FILE_NOT_FOUND"));
            }
        } catch (Exception e) {
            log.error("Vault deletion failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private String detectMediaType(String contentType) {
        if (contentType == null) return "TEXT";
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        return "LINK";
    }
}