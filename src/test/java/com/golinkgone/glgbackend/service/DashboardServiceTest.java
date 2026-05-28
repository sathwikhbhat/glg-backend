package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickStats;
import com.golinkgone.glgbackend.entity.DashboardResponse;
import com.golinkgone.glgbackend.entity.LinkSummary;
import com.golinkgone.glgbackend.repository.DashboardReadRepository;
import com.golinkgone.glgbackend.repository.DashboardReadRepository.LifetimeTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID LINK_ID = UUID.randomUUID();

    @Mock DashboardReadRepository readRepository;

    private final Executor sameThreadExecutor = Runnable::run;

    private DashboardService service() {
        DashboardService s = new DashboardService(readRepository, sameThreadExecutor);
        ReflectionTestUtils.setField(s, "self", s);
        return s;
    }

    @Test
    void getDashboard_assemblesSummaryAndDynamicTimeline() {
        when(readRepository.fetchLifetimeTotals(LINK_ID)).thenReturn(new LifetimeTotals(10L, 4L));
        when(readRepository.fetchLogTimeline(eq(LINK_ID), any(), any(), eq("hour"), eq("Asia/Kolkata")))
                .thenReturn(List.of(new ClickStats(OffsetDateTime.now(ZoneOffset.UTC), 3L, 1L, 2L)));
        when(readRepository.fetchTopCountries(eq(LINK_ID), eq(50))).thenReturn(List.of());
        when(readRepository.fetchTopCities(eq(LINK_ID), eq(15))).thenReturn(List.of());
        when(readRepository.fetchDeviceBreakdown(LINK_ID)).thenReturn(List.of());

        DashboardResponse resp = service().getDashboard(LINK_ID, "24h", "hour", "Asia/Kolkata");

        assertThat(resp.totalClicks()).isEqualTo(10);
        assertThat(resp.newVisitors()).isEqualTo(4);
        assertThat(resp.totals()).singleElement()
                .satisfies(c -> {
                    assertThat(c.total()).isEqualTo(3);
                    assertThat(c.newVisitors()).isEqualTo(1);
                    assertThat(c.uniqueVisitors()).isEqualTo(2);
                });
    }

    @Test
    void getDashboard_all_usesMonthlyTimeline_andSkipsLogQuery() {
        when(readRepository.fetchLifetimeTotals(LINK_ID)).thenReturn(new LifetimeTotals(0L, 0L));
        when(readRepository.fetchMonthlyTimeline(LINK_ID)).thenReturn(List.of());
        when(readRepository.fetchTopCountries(eq(LINK_ID), eq(50))).thenReturn(List.of());
        when(readRepository.fetchTopCities(eq(LINK_ID), eq(15))).thenReturn(List.of());
        when(readRepository.fetchDeviceBreakdown(LINK_ID)).thenReturn(List.of());

        service().getDashboard(LINK_ID, "all", "week", "UTC");

        verify(readRepository).fetchMonthlyTimeline(LINK_ID);
        verify(readRepository, never()).fetchLogTimeline(any(), any(), any(), any(), any());
    }

    @Test
    void getDashboard_7d_windowSpansSevenDays() {
        when(readRepository.fetchLifetimeTotals(LINK_ID)).thenReturn(new LifetimeTotals(0L, 0L));
        when(readRepository.fetchLogTimeline(eq(LINK_ID), any(), any(), eq("day"), eq("UTC")))
                .thenReturn(List.of());
        when(readRepository.fetchTopCountries(eq(LINK_ID), eq(50))).thenReturn(List.of());
        when(readRepository.fetchTopCities(eq(LINK_ID), eq(15))).thenReturn(List.of());
        when(readRepository.fetchDeviceBreakdown(LINK_ID)).thenReturn(List.of());

        service().getDashboard(LINK_ID, "7d", "day", "UTC");

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(readRepository).fetchLogTimeline(eq(LINK_ID), from.capture(), to.capture(), eq("day"), eq("UTC"));
        long hours = ChronoUnit.HOURS.between(from.getValue(), to.getValue());
        assertThat(hours).isEqualTo(24L * 7);
    }

    @Test
    void getLinkSummary_runsFourReadsAndBundlesThem() {
        when(readRepository.fetchLifetimeTotals(LINK_ID)).thenReturn(new LifetimeTotals(7L, 3L));
        when(readRepository.fetchTopCountries(eq(LINK_ID), eq(50))).thenReturn(List.of());
        when(readRepository.fetchTopCities(eq(LINK_ID), eq(15))).thenReturn(List.of());
        when(readRepository.fetchDeviceBreakdown(LINK_ID)).thenReturn(List.of());

        LinkSummary summary = service().getLinkSummary(LINK_ID);

        assertThat(summary.lifetimeTotals().totalClicks()).isEqualTo(7);
        assertThat(summary.lifetimeTotals().newVisitors()).isEqualTo(3);
        verify(readRepository).fetchTopCountries(LINK_ID, 50);
        verify(readRepository).fetchTopCities(LINK_ID, 15);
        verify(readRepository).fetchDeviceBreakdown(LINK_ID);
    }

    @Test
    void getTimeline_doesNotTouchSummaryReads() {
        when(readRepository.fetchLogTimeline(eq(LINK_ID), any(), any(), eq("hour"), eq("UTC")))
                .thenReturn(List.of());

        service().getTimeline(LINK_ID,"24h", "hour", "UTC");

        verify(readRepository, never()).fetchLifetimeTotals(any());
        verify(readRepository, never()).fetchTopCountries(any(), anyInt());
        verify(readRepository, never()).fetchTopCities(any(), anyInt());
        verify(readRepository, never()).fetchDeviceBreakdown(any());
    }

    @Test
    void getDashboard_unsupportedTimeRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> service().getDashboard(LINK_ID, "2h", "day", "UTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported timeRange");
    }

    @Test
    void getDashboard_unsupportedGranularity_throwsIllegalArgument() {
        assertThatThrownBy(() -> service().getDashboard(LINK_ID, "24h", "minute", "UTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported granularity");
    }

    @Test
    void getDashboard_invalidTz_throwsIllegalArgument() {
        assertThatThrownBy(() -> service().getDashboard(LINK_ID, "24h", "hour", "; DROP TABLE--"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
    }

    @Test
    void getDashboard_caseInsensitiveTimeRange() {
        when(readRepository.fetchLifetimeTotals(LINK_ID)).thenReturn(new LifetimeTotals(0L, 0L));
        when(readRepository.fetchLogTimeline(eq(LINK_ID), any(), any(), eq("hour"), eq("UTC")))
                .thenReturn(List.of());
        when(readRepository.fetchTopCountries(eq(LINK_ID), eq(50))).thenReturn(List.of());
        when(readRepository.fetchTopCities(eq(LINK_ID), eq(15))).thenReturn(List.of());
        when(readRepository.fetchDeviceBreakdown(LINK_ID)).thenReturn(List.of());

        service().getDashboard(LINK_ID, "24H", "hour", "UTC");

        verify(readRepository).fetchLogTimeline(eq(LINK_ID), any(), any(), eq("hour"), eq("UTC"));
    }

    @Test
    void getDashboard_nullGranularityAndTz_defaultsApplied() {
        when(readRepository.fetchLifetimeTotals(LINK_ID)).thenReturn(new LifetimeTotals(0L, 0L));
        when(readRepository.fetchLogTimeline(eq(LINK_ID), any(), any(), eq("day"), eq("UTC")))
                .thenReturn(List.of());
        when(readRepository.fetchTopCountries(eq(LINK_ID), eq(50))).thenReturn(List.of());
        when(readRepository.fetchTopCities(eq(LINK_ID), eq(15))).thenReturn(List.of());
        when(readRepository.fetchDeviceBreakdown(LINK_ID)).thenReturn(List.of());

        service().getDashboard(LINK_ID, "7d", null, null);

        verify(readRepository).fetchLogTimeline(eq(LINK_ID), any(), any(), eq("day"), eq("UTC"));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}