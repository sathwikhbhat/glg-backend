package com.golinkgone.glgbackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets;
    private final long capacity;
    private final long refillPerMinute;

    public RateLimiter(
            @Value("${app.rate-limit.capacity:100}") long capacity,
            @Value("${app.rate-limit.refill-per-minute:100}") long refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    public boolean tryAcquire(String key) {
        Bucket bucket = buckets.get(key, k -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(refillPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
