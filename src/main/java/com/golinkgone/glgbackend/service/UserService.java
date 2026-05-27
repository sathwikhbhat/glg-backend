package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.config.KeyStore;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserService {

    private final SupabaseAdminClient supabaseAdminClient;
    private final WebsiteUrlRepository urlRepository;
    private final KeyStore keyStore;
    private final CacheManager cacheManager;

    public UserService(SupabaseAdminClient supabaseAdminClient, WebsiteUrlRepository urlRepository,
                       KeyStore keyStore, CacheManager cacheManager) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.urlRepository = urlRepository;
        this.keyStore = keyStore;
        this.cacheManager = cacheManager;
    }
    
    public void deleteAccount(UUID userId) {
        List<String> userKeys = urlRepository.findAllShortKeysByUserId(userId);

        supabaseAdminClient.deleteUser(userId.toString());
        log.info("Deleted account {}; cascade removed {} link(s)", userId, userKeys.size());

        evictUserCaches(userKeys, userId);
    }

    private void evictUserCaches(List<String> userKeys, UUID userId) {
        Cache urlCache = cacheManager.getCache("urlCache");
        Cache ownershipCache = cacheManager.getCache("ownershipCache");
        Cache linkSummaryCache = cacheManager.getCache("linkSummaryCache");

        for (String key : userKeys) {
            keyStore.removeKey(key);
            if (urlCache != null) urlCache.evict(key);
            if (linkSummaryCache != null) linkSummaryCache.evict(key);
            if (ownershipCache != null) ownershipCache.evict(key + "_" + userId);
        }

        evictTimelineForShortKeys(userKeys);
    }

    private void evictTimelineForShortKeys(List<String> shortKeys) {
        if (shortKeys.isEmpty()) return;
        Cache timelineCache = cacheManager.getCache("dashboardTimelineCache");
        if (timelineCache == null) return;
        Object nativeCache = timelineCache.getNativeCache();
        if (!(nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeine)) return;

        caffeine.asMap().keySet().removeIf(k -> {
            if (!(k instanceof String s)) return false;
            for (String shortKey : shortKeys) {
                if (s.startsWith(shortKey + "_")) return true;
            }
            return false;
        });
    }
}