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
            @RequestHeader HttpHeaders headers) throws Exception {

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

        // ✅ FIXED: We use payload.data() wrapped in ByteArrayResource
        ByteArrayResource resource = new ByteArrayResource(payload.data()) {
            @Override
            public String getFilename() {
                return mediaId + ".mp4";
            }
        };

        long contentLength = payload.data().length;
        List<HttpRange> ranges = headers.getRange();

        if (ranges != null && !ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(MAX_CHUNK_BYTES, end - start + 1);

            ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaId + ".mp4\"")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                    .body(region);
        } else {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaId + ".mp4\"")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                    .contentLength(contentLength)
                    .body(resource);
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