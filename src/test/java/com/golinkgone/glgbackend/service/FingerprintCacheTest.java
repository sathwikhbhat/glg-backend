package com.golinkgone.glgbackend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintCacheTest {

    private final FingerprintCache cache = new FingerprintCache();

    @Test
    void isKnown_returnsFalse_whenEntryAbsent() {
        assertThat(cache.isKnown("abc123", "hash-x")).isFalse();
    }

    @Test
    void markKnown_makesIsKnownReturnTrue() {
        cache.markKnown("abc123", "hash-x");

        assertThat(cache.isKnown("abc123", "hash-x")).isTrue();
    }

    @Test
    void differentShortKeysWithSameIpHash_areTrackedIndependently() {
        cache.markKnown("abc123", "hash-x");

        assertThat(cache.isKnown("def456", "hash-x")).isFalse();
    }

    @Test
    void differentIpHashesWithSameShortKey_areTrackedIndependently() {
        cache.markKnown("abc123", "hash-x");

        assertThat(cache.isKnown("abc123", "hash-y")).isFalse();
    }
}
