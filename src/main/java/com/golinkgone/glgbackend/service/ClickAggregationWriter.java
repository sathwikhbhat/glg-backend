package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository.DeviceKey;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository.LocationKey;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository.MonthlyKey;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-memory map/reduce + batch write of a drained click batch.
 */
@Slf4j
@Service
public class ClickAggregationWriter {

    private final ClickAggregationRepository repository;

    public ClickAggregationWriter(ClickAggregationRepository repository) {
        this.repository = repository;
    }

    private static LocalDate bucketMonth(OffsetDateTime clickTime) {
        return clickTime.toLocalDate().withDayOfMonth(1);
    }

    private static String deviceTypeName(ClickEventDTO c) {
        return c.deviceType() != null ? c.deviceType().name() : "UNKNOWN";
    }

    @Transactional
    public void writeBatch(List<ClickEventDTO> clicks) {
        if (clicks.isEmpty()) return;

        LinkedHashMap<VisitorKey, Integer> pairPositions = new LinkedHashMap<>();
        List<UniqueVisitorRow> uniqueBatch = new ArrayList<>();
        for (ClickEventDTO c : clicks) {
            VisitorKey k = new VisitorKey(c.linkId(), c.visitorHash());
            if (!pairPositions.containsKey(k)) {
                pairPositions.put(k, uniqueBatch.size());
                uniqueBatch.add(new UniqueVisitorRow(c.linkId(), c.visitorHash()));
            }
        }

        // Batch INSERT ... ON CONFLICT DO NOTHING. int[] gives 1 = newly inserted, 0 = conflict.
        int[] results = repository.batchInsertUniqueVisitorsGlobal(uniqueBatch);
        Set<VisitorKey> globallyNewPairs = new HashSet<>();
        List<VisitorKey> keysInOrder = new ArrayList<>(pairPositions.keySet());
        for (int i = 0; i < results.length; i++) {
            int r = results[i];
            if (r == 1) {
                globallyNewPairs.add(keysInOrder.get(i));
            } else if (r != 0) {
                // See ClickAggregationRepository docstring re: reWriteBatchedInserts.
                if (r == Statement.SUCCESS_NO_INFO) {
                    log.error("batchUpdate returned SUCCESS_NO_INFO — new-visitor accounting "
                            + "is now broken. Check that reWriteBatchedInserts is NOT enabled.");
                } else {
                    log.warn("Unexpected batch result {} at row {}", r, i);
                }
            }
        }

        Set<VisitorKey> claimedAsNew = new HashSet<>();
        List<LogRow> logRows = new ArrayList<>(clicks.size());
        Map<UUID, CountIncrement> linkGlobalInc = new HashMap<>();
        Map<MonthlyKey, CountIncrement> monthlyInc = new HashMap<>();
        Map<DeviceKey, CountIncrement> deviceInc = new HashMap<>();
        Map<LocationKey, CountIncrement> locationInc = new HashMap<>();

        for (ClickEventDTO c : clicks) {
            VisitorKey pair = new VisitorKey(c.linkId(), c.visitorHash());
            boolean newVisitor = globallyNewPairs.contains(pair) && claimedAsNew.add(pair);
            long newDelta = newVisitor ? 1 : 0;

            linkGlobalInc.merge(c.linkId(), CountIncrement.of(1, newDelta), CountIncrement::plus);
            monthlyInc.merge(
                    new MonthlyKey(c.linkId(), bucketMonth(c.clickTime())),
                    CountIncrement.of(1, newDelta),
                    CountIncrement::plus);
            deviceInc.merge(
                    new DeviceKey(c.linkId(), deviceTypeName(c)), CountIncrement.of(1, newDelta), CountIncrement::plus);
            locationInc.merge(
                    new LocationKey(c.linkId(), c.countryCode(), c.cityName()),
                    CountIncrement.of(1, newDelta),
                    CountIncrement::plus);

            logRows.add(new LogRow(c.linkId(), c.clickTime(), c.visitorHash(), newVisitor));
        }

        repository.batchUpsertLinkStatsGlobal(linkGlobalInc);
        repository.batchUpsertLinkStatsMonthly(monthlyInc);
        repository.batchUpsertLinkDeviceStats(deviceInc);
        repository.batchUpsertLinkLocationStats(locationInc);

        repository.batchInsertUniqueVisitorsLog(logRows);

        log.debug(
                "Batched {} clicks → {} unique pairs ({} new); upserts: global=1, monthly={}, device={}, location={}",
                clicks.size(),
                uniqueBatch.size(),
                globallyNewPairs.size(),
                monthlyInc.size(),
                deviceInc.size(),
                locationInc.size());
    }

    private record VisitorKey(UUID linkId, UUID visitorHash) {}

    public record UniqueVisitorRow(UUID linkId, UUID visitorHash) {}

    public record LogRow(UUID linkId, OffsetDateTime clickTime, UUID visitorHash, boolean isNewVisitor) {}

    public record CountIncrement(long totalClicks, long newVisitors) {
        static CountIncrement of(long total, long newV) {
            return new CountIncrement(total, newV);
        }

        CountIncrement plus(CountIncrement other) {
            return new CountIncrement(this.totalClicks + other.totalClicks, this.newVisitors + other.newVisitors);
        }
    }
}
