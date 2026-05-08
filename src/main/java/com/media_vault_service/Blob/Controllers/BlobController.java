package com.media_vault_service.Blob.Controllers;

import com.media_vault_service.Blob.Config.AESEncryptor;
import com.media_vault_service.Blob.Services.BlobStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/blob")
@RequiredArgsConstructor
@Slf4j

public class BlobController {
    private final BlobStorageService blobService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("userId") String userId) throws Exception {
        String fileId = blobService.saveMedia(file, userId);
        return ResponseEntity.ok(Map.of("mediaId", fileId, "status", "Encrypted & Vaulted"));
    }
}
