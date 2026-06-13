package com.golinkgone.glgbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;


 //Enforces dual-domain isolation inside the single container:
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HostBasedRoutingFilter extends OncePerRequestFilter {

    private static final String REDIRECT_HOST = "tryglg.ink";
    private static final Set<String> APP_HOSTS = Set.of(
            "api.golinkgone.com",
            "golinkgone.com",
            "www.golinkgone.com");
    private static final Set<String> DEV_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0");
    private static final Set<String> ALWAYS_OPEN_PATHS = Set.of("/health", "/actuator/health");
    private static final Pattern SHORT_KEY_PATH = Pattern.compile("^/[A-Za-z0-9]{6}$");

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String host = req.getServerName() == null ? "" : req.getServerName().toLowerCase();

        // Dev/local always bypassed — tests and dev runs don't set a meaningful Host.
        if (DEV_HOSTS.contains(host)) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        boolean isShortKey = SHORT_KEY_PATH.matcher(path).matches();
        boolean isAlwaysOpen = ALWAYS_OPEN_PATHS.contains(path);

        if (REDIRECT_HOST.equals(host)) {
            if (isAlwaysOpen) {
                chain.doFilter(req, res);
                return;
            }
            if (isShortKey && isReadMethod(req.getMethod())) {
                chain.doFilter(req, res);
                return;
            }
            // Everything else on tryglg.ink — POSTs, /my-links, /account, scans — 404.
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (APP_HOSTS.contains(host)) {
            // Only block short-key-looking paths for redirect methods (GET/HEAD).
            // POST, DELETE, OPTIONS (CORS preflight), etc. must pass through.
            if (isShortKey && isReadMethod(req.getMethod())) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            chain.doFilter(req, res);
            return;
        }
        
        log.debug("Rejected request from unknown host '{}' for path {}", host, path);
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private static boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
