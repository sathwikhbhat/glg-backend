package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.config.KeyStore;
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
    void deleteAccount_snapshotsKeysBeforeHttpDelete() {
        UUID userId = UUID.randomUUID();
        when(urlRepository.findAllShortKeysByUserId(userId)).thenReturn(List.of("abc123"));
        when(cacheManager.getCache("urlCache")).thenReturn(mock(Cache.class));
        when(cacheManager.getCache("ownershipCache")).thenReturn(mock(Cache.class));
        when(cacheManager.getCache("dashboardAnalyticsCache")).thenReturn(mock(Cache.class));

        userService.deleteAccount(userId);

        InOrder order = inOrder(urlRepository, supabaseAdminClient);
        order.verify(urlRepository).findAllShortKeysByUserId(userId);
        order.verify(supabaseAdminClient).deleteUser(userId.toString());
    }

    @Test
    void deleteAccount_evictsAllCachesForUserKeys() {
        UUID userId = UUID.randomUUID();
        when(urlRepository.findAllShortKeysByUserId(userId)).thenReturn(List.of("abc123", "def456"));
        Cache urlCache = mock(Cache.class);
        Cache ownershipCache = mock(Cache.class);
        Cache analyticsCache = mock(Cache.class);
        when(cacheManager.getCache("urlCache")).thenReturn(urlCache);
        when(cacheManager.getCache("ownershipCache")).thenReturn(ownershipCache);
        when(cacheManager.getCache("dashboardAnalyticsCache")).thenReturn(analyticsCache);

        userService.deleteAccount(userId);

        for (String key : List.of("abc123", "def456")) {
            verify(keyStore).removeKey(key);
            verify(urlCache).evict(key);
            verify(ownershipCache).evict(key + "_" + userId);
            verify(analyticsCache).evict(key + "_24h");
            verify(analyticsCache).evict(key + "_7d");
            verify(analyticsCache).evict(key + "_30d");
            verify(analyticsCache).evict(key + "_all");
        }
    }

    @Test
    void deleteAccount_tolerantOfNullCaches() {
        UUID userId = UUID.randomUUID();
        when(urlRepository.findAllShortKeysByUserId(userId)).thenReturn(List.of("abc123"));
        when(cacheManager.getCache("urlCache")).thenReturn(null);
        when(cacheManager.getCache("ownershipCache")).thenReturn(null);
        when(cacheManager.getCache("dashboardAnalyticsCache")).thenReturn(null);

        userService.deleteAccount(userId);

        verify(keyStore).removeKey("abc123");
        verify(supabaseAdminClient).deleteUser(userId.toString());
    }
}
