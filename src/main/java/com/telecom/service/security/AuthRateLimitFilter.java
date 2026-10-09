package com.telecom.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP fixed-window limit on login/registration, complementing the per-account lockout
 * (which cannot stop one client guessing across many accounts). In-memory: fine for a single instance.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public AuthRateLimitFilter(@Value("${app.rate-limit.auth-per-minute}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("POST".equals(request.getMethod())
                && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String ip = request.getRemoteAddr(); // honours X-Forwarded-For when server.forward-headers-strategy is set
        Window w = windows.merge(ip, new Window(now, 1),
                (old, fresh) -> now - old.start() >= WINDOW_MS ? fresh : new Window(old.start(), old.count() + 1));

        if (w.count() > limit) {
            long retryAfter = Math.max(1, (w.start() + WINDOW_MS - now) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }
        if (windows.size() > 10_000) { // keep memory bounded
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
        }
        chain.doFilter(request, response);
    }
}
