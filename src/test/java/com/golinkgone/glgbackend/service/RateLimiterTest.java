package com.golinkgone.glgbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void tryAcquire_allowsUpToCapacity() {
        RateLimiter limiter = new RateLimiter(5, 60);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4"))
                    .as("request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void tryAcquire_rejectsAfterCapacityExhausted() {
        RateLimiter limiter = new RateLimiter(3, 1);

        for (int i = 0; i < 3; i++) limiter.tryAcquire("1.2.3.4");

        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void tryAcquire_isolatesBucketsByKey() {
        RateLimiter limiter = new RateLimiter(2, 1);

        limiter.tryAcquire("1.2.3.4");
        limiter.tryAcquire("1.2.3.4");

        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(limiter.tryAcquire("5.6.7.8")).isTrue();
    }
}
