package com.golinkgone.glgbackend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("urlCache", "dashboardAnalyticsCache",
                "ownershipCache");
        cacheManager.registerCustomCache("urlCache",
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(Duration.ofHours(24))
                        .recordStats()
                        .build());

        cacheManager.registerCustomCache("dashboardAnalyticsCache",
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(1))
                        .recordStats()
                        .build());

        cacheManager.registerCustomCache("ownershipCache",
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(Duration.ofHours(5))
                        .recordStats()
                        .build());

        return cacheManager;
    }
}
