package com.media_vault_service.Blob.ExceptionHandlers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// This annotation maps the exception directly to a 404 response
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Asset not found on physical disk")
public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException(String message) {
        super(message);
    }
}