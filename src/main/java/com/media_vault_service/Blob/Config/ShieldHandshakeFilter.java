package com.media_vault_service.Blob.Config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class ShieldHandshakeFilter extends OncePerRequestFilter {

    @Value("${ghost.shield.key}")
    private String shieldKey;

    @Value("${ghost.gateway.secret}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 🟢 FIX: Allow public access to stream endpoints for both /v1/ and /api/ prefixes
        if (request.getMethod().equalsIgnoreCase("GET") &&
                (uri.startsWith("/api/vault/stream/") || uri.startsWith("/v1/vault/stream/"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String incomingShield = request.getHeader("X-Ghost-Shield-Key");
        String incomingGateway = request.getHeader("X-Gateway-Secret");

        if (incomingShield != null) incomingShield = incomingShield.trim();
        if (incomingGateway != null) incomingGateway = incomingGateway.trim();

        boolean isValidShield = incomingShield != null && incomingShield.equals(shieldKey);
        boolean isValidGateway = incomingGateway != null && incomingGateway.equals(gatewaySecret);

        if (isValidShield || isValidGateway) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("INTRUSION ATTEMPT: Access blocked for {}", uri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Ghost System: Access Denied.");
        }
    }
}