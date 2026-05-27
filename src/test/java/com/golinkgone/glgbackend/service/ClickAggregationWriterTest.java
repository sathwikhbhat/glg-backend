package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import com.golinkgone.glgbackend.entity.DeviceType;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository.DeviceKey;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository.LocationKey;
import com.golinkgone.glgbackend.repository.ClickAggregationRepository.MonthlyKey;
import com.golinkgone.glgbackend.service.ClickAggregationWriter.CountIncrement;
import com.golinkgone.glgbackend.service.ClickAggregationWriter.LogRow;
import com.golinkgone.glgbackend.service.ClickAggregationWriter.UniqueVisitorRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickAggregationWriterTest {

    private static final UUID LINK_A = UUID.randomUUID();
    private static final UUID LINK_B = UUID.randomUUID();
    private static final UUID VISITOR_X = UUID.randomUUID();
    private static final UUID VISITOR_Y = UUID.randomUUID();

    @Mock ClickAggregationRepository repository;
    @InjectMocks ClickAggregationWriter writer;

    @Test
    void emptyBatch_doesNothing() {
        writer.writeBatch(List.of());
        verifyNoInteractions(repository);
    }

    @Test
    void firstAndRepeatVisitor_inSameBatch_credited_onlyOnce() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ClickEventDTO> clicks = List.of(
                click(LINK_A, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", now),
                click(LINK_A, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", now.plusSeconds(1))
        );
        // Only one unique (link, hash) pair → batch of size 1; DB reports 1 = new.
        when(repository.batchInsertUniqueVisitorsGlobal(anyList())).thenReturn(new int[]{1});

        writer.writeBatch(clicks);

        ArgumentCaptor<Map<UUID, CountIncrement>> globalInc =
                ArgumentCaptor.forClass(Map.class);
        verify(repository).batchUpsertLinkStatsGlobal(globalInc.capture());
        CountIncrement inc = globalInc.getValue().get(LINK_A);
        assertThat(inc.totalClicks()).isEqualTo(2);
        assertThat(inc.newVisitors()).isEqualTo(1); // first click credited; second is not new

        ArgumentCaptor<List<LogRow>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).batchInsertUniqueVisitorsLog(logCaptor.capture());
        assertThat(logCaptor.getValue()).hasSize(2);
        assertThat(logCaptor.getValue().get(0).isNewVisitor()).isTrue();
        assertThat(logCaptor.getValue().get(1).isNewVisitor()).isFalse();
    }

    @Test
    void existingVisitor_dbReports0_isNotCounted() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ClickEventDTO> clicks = List.of(
                click(LINK_A, VISITOR_X, DeviceType.DESKTOP, "US", "NYC", now)
        );
        when(repository.batchInsertUniqueVisitorsGlobal(anyList())).thenReturn(new int[]{0});

        writer.writeBatch(clicks);

        ArgumentCaptor<Map<UUID, CountIncrement>> globalInc = ArgumentCaptor.forClass(Map.class);
        verify(repository).batchUpsertLinkStatsGlobal(globalInc.capture());
        assertThat(globalInc.getValue().get(LINK_A).newVisitors()).isZero();

        ArgumentCaptor<List<LogRow>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).batchInsertUniqueVisitorsLog(logCaptor.capture());
        assertThat(logCaptor.getValue().get(0).isNewVisitor()).isFalse();
    }

    @Test
    void newVisitorAttribution_pinnedToFirstDimensions() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ClickEventDTO> clicks = List.of(
                click(LINK_A, VISITOR_X, DeviceType.PHONE,   "IN", "Bengaluru", now),
                click(LINK_A, VISITOR_X, DeviceType.DESKTOP, "US", "NYC",       now.plusMinutes(5))
        );
        when(repository.batchInsertUniqueVisitorsGlobal(anyList())).thenReturn(new int[]{1});

        writer.writeBatch(clicks);

        ArgumentCaptor<Map<DeviceKey, CountIncrement>> deviceCap = ArgumentCaptor.forClass(Map.class);
        verify(repository).batchUpsertLinkDeviceStats(deviceCap.capture());
        Map<DeviceKey, CountIncrement> devices = deviceCap.getValue();
        assertThat(devices.get(new DeviceKey(LINK_A, "PHONE")).newVisitors()).isEqualTo(1);
        assertThat(devices.get(new DeviceKey(LINK_A, "DESKTOP")).newVisitors()).isZero();
        assertThat(devices.get(new DeviceKey(LINK_A, "PHONE")).totalClicks()).isEqualTo(1);
        assertThat(devices.get(new DeviceKey(LINK_A, "DESKTOP")).totalClicks()).isEqualTo(1);

        ArgumentCaptor<Map<LocationKey, CountIncrement>> locCap = ArgumentCaptor.forClass(Map.class);
        verify(repository).batchUpsertLinkLocationStats(locCap.capture());
        Map<LocationKey, CountIncrement> locs = locCap.getValue();
        assertThat(locs.get(new LocationKey(LINK_A, "IN", "Bengaluru")).newVisitors()).isEqualTo(1);
        assertThat(locs.get(new LocationKey(LINK_A, "US", "NYC")).newVisitors()).isZero();
    }

    @Test
    void deduplicatesGlobalUvBatch_evenWithManyDuplicateClicks() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ClickEventDTO> clicks = List.of(
                click(LINK_A, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", now),
                click(LINK_A, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", now.plusSeconds(1)),
                click(LINK_A, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", now.plusSeconds(2)),
                click(LINK_B, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", now.plusSeconds(3))
        );
        // Expect 2 unique (link, visitor) pairs sent to dedup table.
        when(repository.batchInsertUniqueVisitorsGlobal(anyList())).thenReturn(new int[]{1, 1});

        writer.writeBatch(clicks);

        ArgumentCaptor<List<UniqueVisitorRow>> uvCap = ArgumentCaptor.forClass(List.class);
        verify(repository).batchInsertUniqueVisitorsGlobal(uvCap.capture());
        assertThat(uvCap.getValue()).hasSize(2);
    }

    @Test
    void monthlyBucket_truncatesToFirstOfMonth() {
        OffsetDateTime when = OffsetDateTime.of(2026, 3, 15, 12, 34, 56, 0, ZoneOffset.UTC);
        when(repository.batchInsertUniqueVisitorsGlobal(anyList())).thenReturn(new int[]{1});

        writer.writeBatch(List.of(click(LINK_A, VISITOR_X, DeviceType.PHONE, "IN", "Bengaluru", when)));

        ArgumentCaptor<Map<MonthlyKey, CountIncrement>> capt = ArgumentCaptor.forClass(Map.class);
        verify(repository).batchUpsertLinkStatsMonthly(capt.capture());
        MonthlyKey expected = new MonthlyKey(LINK_A, LocalDate.of(2026, 3, 1));
        assertThat(capt.getValue()).containsKey(expected);
    }

    @Test
    void differentVisitors_eachCreditedAsNew_inSameLink() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ClickEventDTO> clicks = List.of(
                click(LINK_A, VISITOR_X, DeviceType.PHONE,   "IN", "Bengaluru", now),
                click(LINK_A, VISITOR_Y, DeviceType.DESKTOP, "US", "NYC",       now.plusSeconds(1))
        );
        when(repository.batchInsertUniqueVisitorsGlobal(anyList())).thenReturn(new int[]{1, 1});

        writer.writeBatch(clicks);

        ArgumentCaptor<Map<UUID, CountIncrement>> globalInc = ArgumentCaptor.forClass(Map.class);
        verify(repository).batchUpsertLinkStatsGlobal(globalInc.capture());
        CountIncrement inc = globalInc.getValue().get(LINK_A);
        assertThat(inc.totalClicks()).isEqualTo(2);
        assertThat(inc.newVisitors()).isEqualTo(2);
    }

    private static ClickEventDTO click(UUID linkId, UUID visitor, DeviceType device,
                                       String country, String city, OffsetDateTime ts) {
        return new ClickEventDTO(linkId, visitor, device, country, city, ts);
    }
}
