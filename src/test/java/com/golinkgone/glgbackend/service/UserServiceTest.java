package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.config.KeyStore;
import com.golinkgone.glgbackend.entity.LinkRef;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock SupabaseAdminClient supabaseAdminClient;
    @Mock WebsiteUrlRepository urlRepository;
    @Mock KeyStore keyStore;
    @Mock CacheManager cacheManager;
    @InjectMocks UserService userService;

    @Test
    void deleteAccount_selectsLinksBeforeSupabaseUserDelete() {
        UUID userId = UUID.randomUUID();
        when(urlRepository.findAllLinkRefsByUserId(userId))
                .thenReturn(List.of(new LinkRef("abc123", UUID.randomUUID())));
        when(cacheManager.getCache("urlCache")).thenReturn(mock(Cache.class));
        when(cacheManager.getCache("ownershipCache")).thenReturn(mock(Cache.class));
        when(cacheManager.getCache("linkSummaryCache")).thenReturn(mock(Cache.class));
        when(cacheManager.getCache("dashboardTimelineCache")).thenReturn(mock(Cache.class));

        userService.deleteAccount(userId);

        InOrder order = inOrder(urlRepository, supabaseAdminClient);
        order.verify(urlRepository).findAllLinkRefsByUserId(userId);
        order.verify(supabaseAdminClient).deleteUser(userId.toString());
    }

    @Test
    void deleteAccount_evictsPerKeyCachesForUserKeys() {
        UUID userId = UUID.randomUUID();
        List<LinkRef> links = List.of(
                new LinkRef("abc123", UUID.randomUUID()),
                new LinkRef("def456", UUID.randomUUID()));
        when(urlRepository.findAllLinkRefsByUserId(userId)).thenReturn(links);
        Cache urlCache = mock(Cache.class);
        Cache ownershipCache = mock(Cache.class);
        Cache linkSummaryCache = mock(Cache.class);
        Cache timelineCache = mock(Cache.class);
        when(cacheManager.getCache("urlCache")).thenReturn(urlCache);
        when(cacheManager.getCache("ownershipCache")).thenReturn(ownershipCache);
        when(cacheManager.getCache("linkSummaryCache")).thenReturn(linkSummaryCache);
        when(cacheManager.getCache("dashboardTimelineCache")).thenReturn(timelineCache);

        userService.deleteAccount(userId);

        for (LinkRef link : links) {
            verify(keyStore).removeKey(link.shortKey());
            verify(urlCache).evict(link.shortKey());
            verify(linkSummaryCache).evict(link.linkId());
            verify(ownershipCache).evict(link.shortKey() + "_" + userId);
        }
        verify(timelineCache, never()).clear();
    }

    @Test
    void deleteAccount_tolerantOfNullCaches() {
        UUID userId = UUID.randomUUID();
        when(urlRepository.findAllLinkRefsByUserId(userId))
                .thenReturn(List.of(new LinkRef("abc123", UUID.randomUUID())));
        when(cacheManager.getCache("urlCache")).thenReturn(null);
        when(cacheManager.getCache("ownershipCache")).thenReturn(null);
        when(cacheManager.getCache("linkSummaryCache")).thenReturn(null);
        when(cacheManager.getCache("dashboardTimelineCache")).thenReturn(null);

        userService.deleteAccount(userId);

        verify(keyStore).removeKey("abc123");
        verify(supabaseAdminClient).deleteUser(userId.toString());
    }
}