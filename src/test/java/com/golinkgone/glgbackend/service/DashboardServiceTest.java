package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.DashboardResponse;
import com.golinkgone.glgbackend.entity.LifetimeTotals;
import com.golinkgone.glgbackend.repository.ClickEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock ClickEventRepository repository;

    private DashboardService dashboardService(ClickEventRepository repo) {
        return new DashboardService(repo, Runnable::run);
    }

    @Test
    void getDashboard_24h_usesHourGranularity() {
        when(repository.getLifetimeTotals("abc123")).thenReturn(totals(10, 4));
        when(repository.getTotals(eq("abc123"), any(), any(), eq("hour"))).thenReturn(Collections.emptyList());
        when(repository.getTopCountries(eq("abc123"), any(), any())).thenReturn(Collections.emptyList());
        when(repository.getTopCities(eq("abc123"), any(), any())).thenReturn(Collections.emptyList());

        DashboardResponse resp = dashboardService(repository).getDashboard("abc123", "24h");

        assertThat(resp.totalClicks()).isEqualTo(10);
        assertThat(resp.uniqueClicks()).isEqualTo(4);
        ArgumentCaptor<String> granularity = ArgumentCaptor.forClass(String.class);
        verify(repository).getTotals(eq("abc123"), any(), any(), granularity.capture());
        assertThat(granularity.getValue()).isEqualTo("hour");
    }

    @Test
    void getDashboard_7d_usesDayGranularity() {
        when(repository.getLifetimeTotals("abc123")).thenReturn(totals(0, 0));
        when(repository.getTotals(any(), any(), any(), eq("day"))).thenReturn(Collections.emptyList());
        when(repository.getTopCountries(any(), any(), any())).thenReturn(Collections.emptyList());
        when(repository.getTopCities(any(), any(), any())).thenReturn(Collections.emptyList());

        dashboardService(repository).getDashboard("abc123", "7d");

        verify(repository).getTotals(eq("abc123"), any(), any(), eq("day"));
    }

    @Test
    void getDashboard_all_usesMonthGranularityAndEpochStart() {
        when(repository.getLifetimeTotals("abc123")).thenReturn(totals(0, 0));
        when(repository.getTotals(any(), any(), any(), eq("month"))).thenReturn(Collections.emptyList());
        when(repository.getTopCountries(any(), any(), any())).thenReturn(Collections.emptyList());
        when(repository.getTopCities(any(), any(), any())).thenReturn(Collections.emptyList());

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        dashboardService(repository).getDashboard("abc123", "all");

        verify(repository).getTotals(eq("abc123"), from.capture(), any(), eq("month"));
        assertThat(from.getValue()).isEqualTo(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void getDashboard_unsupportedTimeRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> dashboardService(repository).getDashboard("abc123", "2h"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported timeRange");
    }

    @Test
    void getDashboard_caseInsensitiveTimeRange() {
        when(repository.getLifetimeTotals("abc123")).thenReturn(totals(0, 0));
        when(repository.getTotals(any(), any(), any(), eq("hour"))).thenReturn(Collections.emptyList());
        when(repository.getTopCountries(any(), any(), any())).thenReturn(Collections.emptyList());
        when(repository.getTopCities(any(), any(), any())).thenReturn(Collections.emptyList());

        dashboardService(repository).getDashboard("abc123", "24H");

        verify(repository).getTotals(eq("abc123"), any(), any(), eq("hour"));
    }

    private LifetimeTotals totals(long total, long unique) {
        return new LifetimeTotals() {
            @Override public long getTotalClicks() { return total; }
            @Override public long getUniqueClicks() { return unique; }
        };
    }
}
