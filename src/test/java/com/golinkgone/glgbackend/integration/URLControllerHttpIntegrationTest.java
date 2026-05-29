package com.golinkgone.glgbackend.integration;

import com.golinkgone.glgbackend.config.RateLimitInterceptor;
import com.golinkgone.glgbackend.config.SecurityConfig;
import com.golinkgone.glgbackend.config.WebMvcConfig;
import com.golinkgone.glgbackend.controller.URLController;
import com.golinkgone.glgbackend.entity.CreateResponse;
import com.golinkgone.glgbackend.exception.GlobalExceptionHandler;
import com.golinkgone.glgbackend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = URLControllerHttpIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.allowed-origins=http://localhost:5173",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test/",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.test/jwks.json",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        }
)
class URLControllerHttpIntegrationTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    int port;

    @MockitoBean
    URLShortenerService urlShortenerService;

    @MockitoBean
    DashboardService dashboardService;

    @MockitoBean
    OwnerService ownerService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    RateLimiter rateLimiter;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
    }

    @Test
    void httpFlow_createAndRedirect_usesRealServerStack() throws Exception {
        when(urlShortenerService.createShortLink(eq("https://example.com"), any()))
                .thenReturn(new CreateResponse("http://localhost:" + port + "/abc123", new byte[]{1, 2, 3}));
        when(urlShortenerService.redirectUrl(eq("abc123"), any(), any(), any(), eq(true)))
                .thenReturn("https://example.com/target");

        HttpResponse<String> health = CLIENT.send(
                HttpRequest.newBuilder(uri("/health")).GET().timeout(Duration.ofSeconds(3)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("GoLinkGone OK");

        HttpResponse<String> create = CLIENT.send(
                HttpRequest.newBuilder(uri("/create"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"originalUrl\":\"https://example.com\"}"))
                        .timeout(Duration.ofSeconds(3))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(create.statusCode()).isEqualTo(200);
        assertThat(create.body()).contains("\"shortUrl\":\"http://localhost:" + port + "/abc123\"");

        HttpResponse<Void> redirect = CLIENT.send(
                HttpRequest.newBuilder(uri("/abc123")).GET().timeout(Duration.ofSeconds(3)).build(),
                HttpResponse.BodyHandlers.discarding()
        );
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("location")).contains("https://example.com/target");
    }

    @Test
    void boundedHealthLoad_handlesSmallConcurrentBurst() throws Exception {
        int totalRequests = 60;
        int concurrency = 6;
        HttpRequest request = HttpRequest.newBuilder(uri("/health"))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();

        long started = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(concurrency)) {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < totalRequests; i++) {
                responses.add(executor.submit(() -> CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()));
            }

            for (Future<Integer> response : responses) {
                assertThat(response.get()).isEqualTo(200);
            }
        }

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(elapsedMillis).isLessThan(10_000);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            URLController.class,
            SecurityConfig.class,
            WebMvcConfig.class,
            RateLimitInterceptor.class,
            GlobalExceptionHandler.class
    })
    static class TestApp {
    }
}
