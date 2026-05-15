package com.golinkgone.glgbackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class FingerprintCache {

    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .maximumSize(1_000_000)
            .expireAfterWrite(Duration.ofHours(24))
            .recordStats()
            .build();

    public boolean isKnown(String shortKey, String ipHash) {
        return seen.getIfPresent(key(shortKey, ipHash)) != null;
    }

    public void markKnown(String shortKey, String ipHash) {
        seen.put(key(shortKey, ipHash), Boolean.TRUE);
    }

    private static String key(String shortKey, String ipHash) {
        return shortKey + ":" + ipHash;
    }
}