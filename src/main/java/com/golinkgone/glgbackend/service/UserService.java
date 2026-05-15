package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.config.KeyStore;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
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

        evictUserCaches(userKeys, userId);
    }

    private void evictUserCaches(List<String> userKeys, UUID userId) {
        Cache urlCache = cacheManager.getCache("urlCache");
        Cache ownershipCache = cacheManager.getCache("ownershipCache");
        Cache analyticsCache = cacheManager.getCache("dashboardAnalyticsCache");

        for (String key : userKeys) {
            keyStore.removeKey(key);
            if (urlCache != null) urlCache.evict(key);
            if (ownershipCache != null) ownershipCache.evict(key + "_" + userId);
            if (analyticsCache != null) {
                analyticsCache.evict(key + "_24h");
                analyticsCache.evict(key + "_7d");
                analyticsCache.evict(key + "_30d");
                analyticsCache.evict(key + "_all");
            }
        }
    }
}