package com.media_vault_service.Blob.Controllers;

import com.media_vault_service.Blob.Services.BlobStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
public class VaultController {

    private final BlobStorageService blobService;

    // Upload Encrypted Media
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) throws Exception {

        String ghostPath = blobService.saveMedia(file, userId);

        Map<String, String> response = new HashMap<>();
        response.put("mediaId", ghostPath);
        response.put("status", "VAULTED");

        return ResponseEntity.ok(response);
    }

    // Stream & Decrypt Media
    @GetMapping("/stream/{mediaId}")
    public ResponseEntity<Resource> streamMedia(@PathVariable String mediaId) throws Exception {
        InputStream stream = blobService.getDecryptedStream(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4")) // Or dynamic detection
                .body(new InputStreamResource(stream));
    }
}
