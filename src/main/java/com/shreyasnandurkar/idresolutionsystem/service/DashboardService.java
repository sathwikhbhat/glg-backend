package com.shreyasnandurkar.idresolutionsystem.service;

import com.shreyasnandurkar.idresolutionsystem.entity.*;
import com.shreyasnandurkar.idresolutionsystem.repository.ClickEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class DashboardService {

    private final ClickEventRepository clickEventRepository;
    private final Executor dashboardExecutor;

    public DashboardService(ClickEventRepository clickEventRepository,
                            @Qualifier("dashboardExecutor") Executor dashboardExecutor) {
        this.clickEventRepository = clickEventRepository;
        this.dashboardExecutor = dashboardExecutor;
    }

    @Cacheable(value = "dashboardAnalyticsCache", key = "#shortKey + '_' + #timeRange", sync = true)
    public DashboardResponse getDashboard(String shortKey, String timeRange) {

        log.debug("Cache miss – querying DB for shortKey={} timeRange={}", shortKey, timeRange);

        TimeWindow window = resolveWindow(timeRange);

        CompletableFuture<List<ClickStats>> totalsFuture = CompletableFuture.supplyAsync(
                () -> clickEventRepository.getTotals(shortKey, window.from(), window.to(), window.granularity()),
                dashboardExecutor
        );

        CompletableFuture<List<CountryStats>> countriesFuture = CompletableFuture.supplyAsync(
                () -> clickEventRepository.getTopCountries(shortKey, window.from(), window.to()),
                dashboardExecutor
        );

        CompletableFuture<List<CityStats>> citiesFuture = CompletableFuture.supplyAsync(
                () -> clickEventRepository.getTopCities(shortKey, window.from(), window.to()),
                dashboardExecutor
        );

        CompletableFuture<LifetimeTotals> lifetimeFuture = CompletableFuture.supplyAsync(
                () -> clickEventRepository.getLifetimeTotals(shortKey),
                dashboardExecutor
        );

        try {
            CompletableFuture.allOf(totalsFuture, countriesFuture, citiesFuture, lifetimeFuture).join();
            LifetimeTotals lifetime = lifetimeFuture.join();

            return new DashboardResponse(
                    lifetime.getTotalClicks(),
                    lifetime.getUniqueClicks(),
                    totalsFuture.join(),
                    countriesFuture.join(),
                    citiesFuture.join()
            );
        }catch (CompletionException ex) {
            log.error("Failed to load dashboard analytics for shortKey={} timeRange={}", shortKey, timeRange, ex);
            throw new IllegalStateException("Failed to load dashboard analytics", ex);
        }
    }

    private TimeWindow resolveWindow(String timeRange) {
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        return switch (timeRange.toLowerCase()) {
            case "24h" -> new TimeWindow(to.minusHours(24), to, "hour");
            case "7d" -> new TimeWindow(to.minusDays(7), to, "day");
            case "30d" -> new TimeWindow(to.minusDays(30), to, "day");
            case "all" -> new TimeWindow(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), to, "month");
            default -> throw new IllegalArgumentException(
                    "Unsupported timeRange '%s'. Use: 24h | 7d | 30d | all".formatted(timeRange));
        };
    }

    private record TimeWindow(OffsetDateTime from, OffsetDateTime to, String granularity) {
    }
}
