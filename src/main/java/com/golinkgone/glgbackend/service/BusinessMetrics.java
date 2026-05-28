package com.golinkgone.glgbackend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Business metrics. Counters track per-process rates; gauges are refreshed
 * from the database on a schedule to avoid a query on every scrape.
 */
@Slf4j
@Component
public class BusinessMetrics {

    private final JdbcTemplate jdbc;

    private final Counter linksCreated;
    private final Counter redirectsServed;

    private final AtomicLong totalLinks = new AtomicLong();
    private final AtomicLong totalRedirects = new AtomicLong();
    private final AtomicLong totalLinkOwners = new AtomicLong();

    public BusinessMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.linksCreated = Counter.builder("glg.links.created")
                .description("Links created since process start")
                .register(registry);
        this.redirectsServed = Counter.builder("glg.redirects.served")
                .description("Redirects served since process start")
                .register(registry);
        registry.gauge("glg.links.total", totalLinks);
        registry.gauge("glg.redirects.total", totalRedirects);
        registry.gauge("glg.link.owners.total", totalLinkOwners);
    }

    public void recordLinkCreated() {
        linksCreated.increment();
    }

    public void recordRedirectServed() {
        redirectsServed.increment();
    }

    @Scheduled(fixedDelayString = "${app.metrics.refresh-ms:60000}",
            initialDelayString = "${app.metrics.initial-delay-ms:10000}")
    public void refreshTotals() {
        try {
            totalLinks.set(queryLong("SELECT COUNT(*) FROM website_url"));
            totalRedirects.set(queryLong("SELECT COALESCE(SUM(total_clicks), 0) FROM link_stats_global"));
            totalLinkOwners.set(queryLong(
                    "SELECT COUNT(DISTINCT user_id) FROM website_url WHERE user_id IS NOT NULL"));
        } catch (Exception ex) {
            log.warn("Failed to refresh business metric gauges: {}", ex.getMessage());
        }
    }

    private long queryLong(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}