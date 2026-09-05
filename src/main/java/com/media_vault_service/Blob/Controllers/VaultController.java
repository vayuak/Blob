package com.media_vault_service.Blob.Controllers;

import com.media_vault_service.Blob.Services.BlobStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
public class VaultController {

    private final BlobStorageService blobService;

    @org.springframework.beans.factory.annotation.Value("${vault.max.filesize:104857600}")
    private long maxFileSizeBytes;

    @GetMapping("/stream/{mediaId}")
    public ResponseEntity<Resource> streamMedia(@PathVariable String mediaId) throws Exception {
        BlobStorageService.DecryptedPayload payload = blobService.getDecryptedPayload(mediaId);

        String rawType = payload.contentType();
        // 🟢 FIX: Mobile ExoPlayer rejects application/octet-stream. Fallback to video/mp4
        if (rawType == null || rawType.equalsIgnoreCase("application/octet-stream")) {
            rawType = "video/mp4";
        }

        MediaType mediaType = MediaType.parseMediaType(rawType);
        ByteArrayResource resource = new ByteArrayResource(payload.data());

        return ResponseEntity.ok()
                // 🟢 FIX: Mandatory headers for Android ExoPlayer & iOS AVPlayer
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaId + ".mp4\"")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                .contentType(mediaType)
                .contentLength(payload.data().length)
                .body(resource);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        try {
            if (file.getSize() > maxFileSizeBytes) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Map.of("error", "SIZE_VIOLATION", "message", "Media payload exceeds size limit."));
            }

            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("audio/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "FORMAT_PROHIBITED", "message", "Audio uploads are prohibited."));
            }

            log.info("Vault executing encrypted write operations for user handle reference: {}", userId);
            String ghostPath = blobService.saveMedia(file, userId);
            String gatewayRelativePath = "/v1/vault/stream/" + ghostPath;

            return ResponseEntity.ok(Map.of(
                    "mediaId", ghostPath,
                    "mediaUrl", gatewayRelativePath,
                    "mediaType", detectMediaType(contentType),
                    "avatarUrl", gatewayRelativePath,
                    "profilePictureUrl", gatewayRelativePath,
                    "status", "VAULTED"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{mediaId}")
    public ResponseEntity<?> deleteMediaEndpoint(@PathVariable String mediaId) {
        try {
            log.info("Vault executing secure purge operations for media footprint: {}", mediaId);
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