package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.config.AppProperties;
import com.golinkgone.glgbackend.config.KeyStore;
import com.golinkgone.glgbackend.entity.CreateResponse;
import com.golinkgone.glgbackend.entity.ResolvedLink;
import com.golinkgone.glgbackend.entity.WebsiteUrl;
import com.golinkgone.glgbackend.exception.ShortKeyNotFoundException;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import com.google.zxing.WriterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class URLShortenerServiceTest {

    @Mock AppProperties appProperties;
    @Mock WebsiteUrlRepository repository;
    @Mock AnalyticsService analyticsService;
    @Mock UrlLookupService urlLookupService;
    @Mock QRCodeService qrService;
    @Mock KeyStore keyStore;
    @Mock CacheManager cacheManager;

    @InjectMocks URLShortenerService service;

    @BeforeEach
    void stubBaseUrl() {
        org.mockito.Mockito.lenient().when(appProperties.getBaseUrl()).thenReturn("https://go.example");
    }

    @Test
    void createShortLink_rejectsMalformedUri() {
        assertThatThrownBy(() -> service.createShortLink("not a url", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createShortLink_rejectsNonHttpScheme() {
        assertThatThrownBy(() -> service.createShortLink("ftp://example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    @Test
    void createShortLink_rejectsHostWithoutDot() {
        assertThatThrownBy(() -> service.createShortLink("http://localhost", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void createShortLink_succeedsOnFirstAttempt() throws IOException, WriterException {
        when(qrService.generateQrImage(any(), anyInt(), anyInt())).thenReturn(new byte[]{1, 2, 3});

        CreateResponse response = service.createShortLink("https://example.com/page", UUID.randomUUID());

        assertThat(response.shortUrl()).startsWith("https://go.example/");
        assertThat(response.shortUrl()).hasSize("https://go.example/".length() + 6);
        assertThat(response.qrCode()).containsExactly(1, 2, 3);
        verify(repository).saveAndFlush(any(WebsiteUrl.class));
        verify(keyStore).addKey(any());
    }

    @Test
    void createShortLink_retriesOnCollision_thenSucceeds() throws Exception {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .doReturn(null)
                .when(repository).saveAndFlush(any(WebsiteUrl.class));
        when(qrService.generateQrImage(any(), anyInt(), anyInt())).thenReturn(new byte[0]);

        CreateResponse response = service.createShortLink("https://example.com", null);

        assertThat(response.shortUrl()).startsWith("https://go.example/");
        verify(repository, times(2)).saveAndFlush(any(WebsiteUrl.class));
        verify(keyStore, times(1)).removeKey(any());
    }

    @Test
    void createShortLink_returnsNullQr_whenGenerationFails() throws Exception {
        when(qrService.generateQrImage(any(), anyInt(), anyInt())).thenThrow(new IOException("boom"));

        CreateResponse response = service.createShortLink("https://example.com", null);

        assertThat(response.qrCode()).isNull();
    }

    @Test
    void redirectUrl_rejectsBlankShortKey() {
        assertThatThrownBy(() -> service.redirectUrl(" ", "1.2.3.4", "ua", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redirectUrl_throws404_whenKeyStoreMisses() {
        when(keyStore.contains("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.redirectUrl("missing", "1.2.3.4", "ua", null))
                .isInstanceOf(ShortKeyNotFoundException.class);
    }

    @Test
    void redirectUrl_recordsAnalytics_whenLinkHasOwner() {
        when(keyStore.contains("abc123")).thenReturn(true);
        when(urlLookupService.resolveUrl("abc123"))
                .thenReturn(new ResolvedLink("https://target.example", true));

        String url = service.redirectUrl("abc123", "1.2.3.4", "ua", "?1");

        assertThat(url).isEqualTo("https://target.example");
        verify(analyticsService).recordClick("abc123", "1.2.3.4", "ua", "?1");
    }

    @Test
    void redirectUrl_skipsAnalytics_whenLinkIsAnonymous() {
        when(keyStore.contains("abc123")).thenReturn(true);
        when(urlLookupService.resolveUrl("abc123"))
                .thenReturn(new ResolvedLink("https://target.example", false));

        service.redirectUrl("abc123", "1.2.3.4", "ua", null);

        verify(analyticsService, never()).recordClick(any(), any(), any(), any());
    }

    @Test
    void deleteLink_evictsCachesAndKeyStore_whenRowDeleted() {
        UUID userId = UUID.randomUUID();
        when(repository.deleteByShortKeyAndUserId("abc123", userId)).thenReturn(1);
        Cache urlCache = mock(Cache.class);
        Cache analyticsCache = mock(Cache.class);
        Cache ownershipCache = mock(Cache.class);
        when(cacheManager.getCache("urlCache")).thenReturn(urlCache);
        when(cacheManager.getCache("dashboardAnalyticsCache")).thenReturn(analyticsCache);
        when(cacheManager.getCache("ownershipCache")).thenReturn(ownershipCache);

        service.deleteLink("abc123", userId);

        verify(keyStore).removeKey("abc123");
        verify(urlCache).evict("abc123");
        verify(analyticsCache).evict("abc123_24h");
        verify(analyticsCache).evict("abc123_7d");
        verify(analyticsCache).evict("abc123_30d");
        verify(analyticsCache).evict("abc123_all");
        verify(ownershipCache).evict("abc123_" + userId);
    }

    @Test
    void deleteLink_throws404_whenNoRowMatches() {
        UUID userId = UUID.randomUUID();
        when(repository.deleteByShortKeyAndUserId("abc123", userId)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteLink("abc123", userId))
                .isInstanceOf(ShortKeyNotFoundException.class);
        verify(keyStore, never()).removeKey(any());
    }
}
