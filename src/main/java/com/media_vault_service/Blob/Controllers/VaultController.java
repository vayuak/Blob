package com.media_vault_service.Blob.Controllers;

import com.media_vault_service.Blob.Services.BlobStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
public class VaultController {

    private final BlobStorageService blobService;

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    private static final long MAX_CHUNK_BYTES = 2L * 1024 * 1024;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        try {
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Map.of("error", "SIZE_VIOLATION",
                                "message", "Media payloads are capped at 100MB."));
            }

            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("audio/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "FORMAT_PROHIBITED",
                                "message", "Audio uploads are not supported."));
            }

            String ghostPath = blobService.saveMedia(file, userId);

            return ResponseEntity.ok(Map.of(
                    "mediaUrl", "/v1/vault/stream/" + ghostPath,
                    "mediaType", detectMediaType(contentType),
                    "status", "VAULTED"
            ));
        } catch (Exception e) {
            log.error("Vault upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "UPLOAD_FAILED"));
        }
    }

    @RequestMapping(value = "/stream/{mediaId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headMedia(@PathVariable String mediaId) {
        try {
            BlobStorageService.DecryptedPayload payload = blobService.getDecryptedPayload(mediaId);
            byte[] data = payload.data(); // FIXED: Read directly from payload

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(resolveType(payload.contentType(), mediaId));
            headers.setContentLength(data.length);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setCacheControl("public, max-age=31536000, immutable");
            return new ResponseEntity<>(headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stream/{mediaId}")
    public ResponseEntity<?> streamMedia(
            @PathVariable String mediaId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        BlobStorageService.DecryptedPayload payload;
        try {
            payload = blobService.getDecryptedPayload(mediaId);
        } catch (Exception e) {
            log.warn("Vault read failed for {}: {}", mediaId, e.getMessage());
            return ResponseEntity.notFound().build();
        }

        String rawType = payload.contentType();
        if (rawType == null || rawType.equalsIgnoreCase("application/octet-stream")) {
            rawType = "video/mp4";
        }
        MediaType mediaType = resolveType(rawType, mediaId);

        byte[] data = payload.data();
        long totalLength = data.length;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CACHE_CONTROL, "max-age=31536000");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaId + ".mp4\"");

        // 1. If no range is requested, return the whole file
        if (rangeHeader == null || !rangeHeader.toLowerCase().startsWith("bytes=")) {
            headers.setContentLength(totalLength);
            return new ResponseEntity<>(new ByteArrayResource(data), headers, HttpStatus.OK);
        }

        // 2. If mobile player requests a chunk, slice the byte array manually
        try {
            String range = rangeHeader.substring(6).trim();
            int dash = range.indexOf('-');
            long start = Long.parseLong(range.substring(0, dash));
            long end = range.length() > dash + 1 ? Long.parseLong(range.substring(dash + 1)) : totalLength - 1;

            if (end >= totalLength) {
                end = totalLength - 1;
            }

            // Cap the chunk size to our 2MB limit
            long rangeLength = Math.min(MAX_CHUNK_BYTES, end - start + 1);
            end = start + rangeLength - 1;

            // 🟢 FIX: Slice the array manually to bypass the missing ResourceRegion converter
            byte[] chunk = new byte[(int) rangeLength];
            System.arraycopy(data, (int) start, chunk, 0, (int) rangeLength);

            headers.setContentLength(rangeLength);
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalLength);

            return new ResponseEntity<>(new ByteArrayResource(chunk), headers, HttpStatus.PARTIAL_CONTENT);

        } catch (Exception e) {
            // If the range is invalid, send a 416 so the player knows exactly how big the file is
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + totalLength);
            return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
    }
    @DeleteMapping("/delete/{mediaId}")
    public ResponseEntity<?> deleteMediaEndpoint(@PathVariable String mediaId) {
        try {
            boolean deleted = blobService.deleteMedia(mediaId);
            return deleted
                    ? ResponseEntity.ok(Map.of("status", "PURGED"))
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "FILE_NOT_FOUND"));
        } catch (Exception e) {
            log.error("Vault deletion failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "DELETE_FAILED"));
        }
    }

    private MediaType resolveType(String storedType, String mediaId) {
        if (storedType != null && !storedType.isBlank()
                && !storedType.equalsIgnoreCase("application/octet-stream")) {
            try {
                return MediaType.parseMediaType(storedType);
            } catch (Exception ignored) { }
        }

        String id = mediaId == null ? "" : mediaId.toLowerCase();
        if (id.endsWith(".mp4") || id.contains(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (id.endsWith(".mov") || id.contains(".mov")) return MediaType.parseMediaType("video/quicktime");
        if (id.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (id.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (id.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (id.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (id.endsWith(".jpg") || id.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String detectMediaType(String contentType) {
        if (contentType == null) return "TEXT";
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        return "LINK";
    }
}