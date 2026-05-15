package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.GeoLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoIPServiceTest {

    private final GeoIPService service = new GeoIPService();

    @Test
    void lookup_returnsUnknown_forNullIp() {
        GeoLocation result = service.lookup(null);

        assertThat(result.country()).isEqualTo("UNKNOWN");
        assertThat(result.city()).isEqualTo("UNKNOWN");
    }

    @Test
    void lookup_returnsUnknown_forNonsenseIp() {
        GeoLocation result = service.lookup("definitely not an ip");

        assertThat(result.country()).isEqualTo("UNKNOWN");
    }

    @Test
    void lookup_returnsUnknown_whenReaderNotInitialized() {
        GeoLocation result = service.lookup("8.8.8.8");

        assertThat(result.country()).isEqualTo("UNKNOWN");
    }

    @Test
    void isReady_falseWhenInitNotCalled() {
        assertThat(service.isReady()).isFalse();
    }
}
