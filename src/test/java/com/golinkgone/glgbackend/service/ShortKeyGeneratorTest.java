package com.golinkgone.glgbackend.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ShortKeyGeneratorTest {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9]{6}$");

    @RepeatedTest(50)
    void generateShortKey_returnsSixCharBase62() {
        String key = ShortKeyGenerator.generateShortKey();

        assertThat(key).hasSize(6);
        assertThat(VALID.matcher(key).matches())
                .as("key '%s' should match [A-Za-z0-9]{6}", key)
                .isTrue();
    }

    @Test
    void generateShortKey_hasLowCollisionRateOverLargeSample() {
        Set<String> seen = new HashSet<>();
        int n = 10_000;
        for (int i = 0; i < n; i++) seen.add(ShortKeyGenerator.generateShortKey());

        assertThat(seen).hasSizeGreaterThan(n - 5);
    }
}
