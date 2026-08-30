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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
public class VaultController {
    public String buildStreamUrl(UUID mediaId) {
        if (mediaId == null) return null;

        // Dynamically captures scheme, host, and port from the inbound request
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/vault/stream/")
                .path(mediaId.toString())
                .toUriString();
    }
    private final BlobStorageService blobService;
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024; // 100MB threshold limit

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_FILE", "message", "Uploaded file cannot be empty."));
            }

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

            // 🟢 FIX: Return correct /api/vault/stream path
            return ResponseEntity.ok(Map.of(
                    "mediaId", ghostPath,
                    "mediaUrl", "/api/vault/stream/" + ghostPath,
                    "mediaType", detectMediaType(contentType),
                    "status", "VAULTED"
            ));
        } catch (Exception e) {
            log.error("Vault upload failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stream/{mediaId}")
    public ResponseEntity<Resource> streamMedia(@PathVariable String mediaId) {
        try {
            BlobStorageService.DecryptedPayload payload = blobService.getDecryptedPayload(mediaId);

            if (payload == null || payload.stream() == null) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = MediaType.parseMediaType(
                    payload.contentType() != null ? payload.contentType() : "application/octet-stream"
            );

            InputStream inputStream = payload.stream();

            // 🟢 FIX: Omit explicit contentLength to let Spring chunk the stream safely
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000, private")
                    .contentType(mediaType)
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            log.error("Streaming failed for media ID {}: {}", mediaId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
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
            log.error("Vault deletion failed for media ID {}: {}", mediaId, e.getMessage());
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