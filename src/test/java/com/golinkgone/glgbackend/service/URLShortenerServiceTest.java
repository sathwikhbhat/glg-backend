package com.golinkgone.glgbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.golinkgone.glgbackend.config.AppProperties;
import com.golinkgone.glgbackend.config.KeyStore;
import com.golinkgone.glgbackend.entity.CreateResponse;
import com.golinkgone.glgbackend.entity.ResolvedLink;
import com.golinkgone.glgbackend.entity.WebsiteUrl;
import com.golinkgone.glgbackend.exception.ShortKeyGenerationException;
import com.golinkgone.glgbackend.exception.ShortKeyNotFoundException;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class URLShortenerServiceTest {

    @Mock
    AppProperties appProperties;

    @Mock
    WebsiteUrlRepository repository;

    @Mock
    ClickIngestionService clickIngestionService;

    @Mock
    UrlLookupService urlLookupService;

    @Mock
    KeyStore keyStore;

    @Mock
    CacheManager cacheManager;

    @Mock
    BusinessMetrics businessMetrics;

    @InjectMocks
    URLShortenerService service;

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
    void createShortLink_succeedsOnFirstAttempt() {
        CreateResponse response = service.createShortLink("https://example.com/page", UUID.randomUUID());

        assertThat(response.shortUrl()).startsWith("https://go.example/");
        assertThat(response.shortUrl()).hasSize("https://go.example/".length() + 6);
        verify(repository).saveAndFlush(any(WebsiteUrl.class));
        verify(keyStore).addKey(any());
    }

    @Test
    void createShortLink_retriesOnCollision_thenSucceeds() throws Exception {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .doReturn(null)
                .when(repository)
                .saveAndFlush(any(WebsiteUrl.class));

        CreateResponse response = service.createShortLink("https://example.com", null);

        assertThat(response.shortUrl()).startsWith("https://go.example/");
        verify(repository, times(2)).saveAndFlush(any(WebsiteUrl.class));
        verify(keyStore, times(1)).removeKey(any());
    }

    @Test
    void createShortLink_rejectsTooLongUrl() {
        String longUrl = "https://example.com/" + "a".repeat(1100);

        assertThatThrownBy(() -> service.createShortLink(longUrl, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1024");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createShortLink_throwsAfterMaxCollisionAttempts() {
        when(repository.saveAndFlush(any(WebsiteUrl.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.createShortLink("https://example.com", null))
                .isInstanceOf(ShortKeyGenerationException.class);
        verify(repository, times(3)).saveAndFlush(any(WebsiteUrl.class));
    }

    @Test
    void redirectUrl_rejectsBlankShortKey() {
        assertThatThrownBy(() -> service.redirectUrl(" ", "1.2.3.4", "ua", null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redirectUrl_throws404_whenKeyStoreMisses() {
        when(keyStore.contains("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.redirectUrl("missing", "1.2.3.4", "ua", null, true))
                .isInstanceOf(ShortKeyNotFoundException.class);
    }

    @Test
    void redirectUrl_enqueuesClick_whenLinkHasOwner() {
        UUID linkId = UUID.randomUUID();
        when(keyStore.contains("abc123")).thenReturn(true);
        when(urlLookupService.resolveUrl("abc123"))
                .thenReturn(new ResolvedLink(linkId, "https://target.example", true));

        String url = service.redirectUrl("abc123", "1.2.3.4", "ua", "?1", true);

        assertThat(url).isEqualTo("https://target.example");
        verify(clickIngestionService).enqueueClick(eq(linkId), eq("1.2.3.4"), eq("ua"), eq("?1"));
    }

    @Test
    void redirectUrl_skipsAnalytics_whenLinkIsAnonymous() {
        when(keyStore.contains("abc123")).thenReturn(true);
        when(urlLookupService.resolveUrl("abc123"))
                .thenReturn(new ResolvedLink(UUID.randomUUID(), "https://target.example", false));

        service.redirectUrl("abc123", "1.2.3.4", "ua", null, true);

        verify(clickIngestionService, never()).enqueueClick(any(), any(), any(), any());
    }

    @Test
    void deleteLink_evictsCachesAndKeyStore_whenRowDeleted() {
        UUID userId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        when(repository.findLinkIdByShortKeyAndUserId("abc123", userId)).thenReturn(Optional.of(linkId));
        Cache urlCache = mock(Cache.class);
        Cache linkSummaryCache = mock(Cache.class);
        Cache timelineCache = mock(Cache.class);
        Cache ownershipCache = mock(Cache.class);
        when(cacheManager.getCache("urlCache")).thenReturn(urlCache);
        when(cacheManager.getCache("linkSummaryCache")).thenReturn(linkSummaryCache);
        when(cacheManager.getCache("dashboardTimelineCache")).thenReturn(timelineCache);
        when(cacheManager.getCache("ownershipCache")).thenReturn(ownershipCache);

        service.deleteLink("abc123", userId);

        verify(repository).deleteByShortKeyAndUserId("abc123", userId);
        verify(keyStore).removeKey("abc123");
        verify(urlCache).evict("abc123");
        verify(linkSummaryCache).evict(linkId);
        verify(timelineCache, never()).clear();
        verify(ownershipCache).evict("abc123_" + userId);
    }

    @Test
    void deleteLink_throws404_whenNoRowMatches() {
        UUID userId = UUID.randomUUID();
        when(repository.findLinkIdByShortKeyAndUserId("abc123", userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteLink("abc123", userId)).isInstanceOf(ShortKeyNotFoundException.class);
        verify(repository, never()).deleteByShortKeyAndUserId(any(), any());
        verify(keyStore, never()).removeKey(any());
    }
}
