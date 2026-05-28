package com.golinkgone.glgbackend.config;

import com.golinkgone.glgbackend.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Pattern SHORT_KEY_PATH = Pattern.compile("^/[A-Za-z0-9]{6}$");

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        // Redirect reads are high-volume and rate-limited by Cloudflare at the edge.
        if (isRedirectRead(request)) {
            return true;
        }

        if (rateLimiter.tryAcquire(request.getRemoteAddr())) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.getWriter().write("{\"message\":\"Too many requests\"}");
        return false;
    }

    private static boolean isRedirectRead(HttpServletRequest request) {
        String method = request.getMethod();
        return (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method))
                && SHORT_KEY_PATH.matcher(request.getRequestURI()).matches();
    }
}