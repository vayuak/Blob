package com.media_vault_service.Blob.Config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class ShieldHandshakeFilter extends OncePerRequestFilter {
    private static final String SHIELD_KEY = "PermanentSecret999";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, java.io.IOException {

        String incomingKey = request.getHeader("X-Ghost-Shield-Key");

        // 🟢 FIXED: Strip the invisible YAML spacing!
        if (incomingKey != null) {
            incomingKey = incomingKey.trim();
        }

        if (SHIELD_KEY.equals(incomingKey)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("INTRUSION ATTEMPT: Direct access to {} blocked from {}", request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain");
            response.getWriter().write("Ghost System: Access Denied. Use the Gateway.");
        }
    }
}