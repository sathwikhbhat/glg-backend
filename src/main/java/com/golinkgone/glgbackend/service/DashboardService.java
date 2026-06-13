package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickStats;
import com.golinkgone.glgbackend.entity.DashboardResponse;
import com.golinkgone.glgbackend.entity.LinkSummary;
import com.golinkgone.glgbackend.repository.DashboardReadRepository;
import com.golinkgone.glgbackend.repository.DashboardReadRepository.LifetimeTotals;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DashboardService {

    private static final Set<String> ALLOWED_TIME_RANGES = Set.of("24h", "7d", "30d", "all");
    private static final Set<String> ALLOWED_GRANULARITIES = Set.of("hour", "day", "week", "month");
    private static final Pattern TZ_PATTERN = Pattern.compile("^[A-Za-z_]+(?:/[A-Za-z_+\\-0-9]+){0,2}$");
    private static final int TOP_COUNTRIES_LIMIT = 50;
    private static final int TOP_CITIES_LIMIT = 15;

    private final DashboardReadRepository readRepository;
    private final Executor dashboardReadExecutor;

    /**
     * Self-reference resolved through the Spring proxy so that calls from
     * Dashboard to the @Cacheable methods go through caching.
     */
    @Autowired
    @Lazy
    private DashboardService self;

    public DashboardService(
            DashboardReadRepository readRepository,
            @Qualifier("dashboardReadExecutor") Executor dashboardReadExecutor) {
        this.readRepository = readRepository;
        this.dashboardReadExecutor = dashboardReadExecutor;
    }

    private static String normalizeTimeRange(String timeRange) {
        if (timeRange == null) return "24h";
        String lower = timeRange.toLowerCase();
        if (!ALLOWED_TIME_RANGES.contains(lower)) {
            throw new IllegalArgumentException(
                    "Unsupported timeRange '%s'. Use: 24h | 7d | 30d | all".formatted(timeRange));
        }
        return lower;
    }

    private static String normalizeGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) return "day";
        String lower = granularity.toLowerCase();
        if (!ALLOWED_GRANULARITIES.contains(lower)) {
            throw new IllegalArgumentException(
                    "Unsupported granularity '%s'. Use: hour | day | week | month".formatted(granularity));
        }
        return lower;
    }

    private static String normalizeTz(String tz) {
        if (tz == null || tz.isBlank()) return "UTC";
        if (!TZ_PATTERN.matcher(tz).matches()) {
            throw new IllegalArgumentException("Invalid timezone: " + tz);
        }
        try {
            ZoneId.of(tz);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid timezone: " + tz);
        }
        return tz;
    }

    public DashboardResponse getDashboard(UUID linkId, String timeRange, String granularity, String tz) {
        String normalizedRange = normalizeTimeRange(timeRange);
        String normalizedGranularity = normalizeGranularity(granularity);
        String normalizedTz = normalizeTz(tz);

        CompletableFuture<LinkSummary> summaryF =
                CompletableFuture.supplyAsync(() -> self.getLinkSummary(linkId), dashboardReadExecutor);

        CompletableFuture<List<ClickStats>> timelineF = CompletableFuture.supplyAsync(
                () -> self.getTimeline(linkId, normalizedRange, normalizedGranularity, normalizedTz),
                dashboardReadExecutor);

        CompletableFuture.allOf(summaryF, timelineF).join();

        LinkSummary summary = summaryF.join();
        LifetimeTotals lifetime = summary.lifetimeTotals();

        return new DashboardResponse(
                lifetime.totalClicks(),
                lifetime.newVisitors(),
                timelineF.join(),
                summary.topCountries(),
                summary.topCities(),
                summary.deviceBreakdown());
    }

    @Cacheable(value = "linkSummaryCache", key = "#linkId", sync = true)
    public LinkSummary getLinkSummary(UUID linkId) {
        log.debug("linkSummaryCache miss – linkId={}", linkId);
        return new LinkSummary(
                readRepository.fetchLifetimeTotals(linkId),
                readRepository.fetchTopCountries(linkId, TOP_COUNTRIES_LIMIT),
                readRepository.fetchTopCities(linkId, TOP_CITIES_LIMIT),
                readRepository.fetchDeviceBreakdown(linkId));
    }

    @Cacheable(
            value = "dashboardTimelineCache",
            key = "#linkId + '_' + #timeRange + '_' + #granularity + '_' + #tz",
            sync = true)
    public List<ClickStats> getTimeline(UUID linkId, String timeRange, String granularity, String tz) {
        log.debug(
                "dashboardTimelineCache miss – linkId={} timeRange={} granularity={} tz={}",
                linkId,
                timeRange,
                granularity,
                tz);
        return "all".equals(timeRange)
                ? readRepository.fetchMonthlyTimeline(linkId)
                : fetchDynamicTimeline(linkId, timeRange, granularity, tz);
    }

    private List<ClickStats> fetchDynamicTimeline(UUID linkId, String range, String granularity, String tz) {
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from =
                switch (range) {
                    case "24h" -> to.minusHours(24);
                    case "7d" -> to.minusDays(7);
                    case "30d" -> to.minusDays(30);
                    default -> throw new IllegalArgumentException("Unexpected range: " + range);
                };
        return readRepository.fetchLogTimeline(linkId, from, to, granularity, tz);
    }
}
