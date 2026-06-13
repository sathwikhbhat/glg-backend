package com.golinkgone.glgbackend.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class GeoIPHealthIndicator implements HealthIndicator {

    private final GeoIPService geoIPService;

    public GeoIPHealthIndicator(GeoIPService geoIPService) {
        this.geoIPService = geoIPService;
    }

    @Override
    public Health health() {
        return geoIPService.isReady()
                ? Health.up().withDetail("maxmind", "ready").build()
                : Health.down().withDetail("maxmind", "database not loaded").build();
    }
}
