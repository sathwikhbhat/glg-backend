package com.golinkgone.glgbackend.controller;

import com.golinkgone.glgbackend.config.SecurityConfig;
import com.golinkgone.glgbackend.exception.ShortKeyNotFoundException;
import com.golinkgone.glgbackend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(URLController.class)
@Import({SecurityConfig.class, JwtDecoderTestConfig.class})
public class URLControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private URLShortenerService urlShortenerService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private OwnerService ownerService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void allowRequestsThroughRateLimiter() {
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /health returns OK message")
    void health_returnsOkMessage() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("GoLinkGone OK"));
    }

    @Test
    @DisplayName("GET /info returns info message")
    void info_returnsInfoMessage() throws Exception {
        mockMvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(content().string("This is GoLinkGone :P"));
    }

    @Test
    @DisplayName("POST /create rejects empty payload")
    void create_rejectsEmptyPayload() throws Exception {
        mockMvc.perform(post("/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /create rejects invalid URL")
    void create_rejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /my-links requires authentication")
    void myLinks_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/my-links"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /account requires authentication")
    void deleteAccount_requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{shortKey}/dashboard requires authentication")
    void dashboard_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/abc123/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /{shortKey} requires authentication")
    void deleteLink_requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/abc123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET unknown short key should not expose 5xx")
    void redirect_unknownShortKey_noServerErrorLeak() throws Exception {
        when(urlShortenerService.redirectUrl(eq("abc123"), any(), any(), any(), anyBoolean()))
                .thenThrow(new ShortKeyNotFoundException("abc123"));

        mockMvc.perform(get("/abc123"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("HEAD unknown short key should not expose 5xx")
    void redirectHead_unknownShortKey_noServerErrorLeak() throws Exception {
        when(urlShortenerService.redirectUrl(eq("abc123"), any(), any(), any(), anyBoolean()))
                .thenThrow(new ShortKeyNotFoundException("abc123"));

        mockMvc.perform(head("/abc123"))
                .andExpect(status().is4xxClientError());
    }

}

@TestConfiguration
class JwtDecoderTestConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> null;
    }
}
