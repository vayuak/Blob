package com.media_vault_service.Blob.ExceptionHandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex) {
        // A "Broken pipe" means the mobile video player (ExoPlayer/AVPlayer)
        // cleanly closed the connection after buffering the chunk it requested.
        log.trace("Client completed buffer and closed video stream (Broken Pipe).");
    }
}