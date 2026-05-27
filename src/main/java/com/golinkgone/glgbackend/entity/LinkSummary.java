package com.golinkgone.glgbackend.entity;

import com.golinkgone.glgbackend.repository.DashboardReadRepository.LifetimeTotals;

import java.util.List;

public record LinkSummary(
        LifetimeTotals lifetimeTotals,
        List<CountryStats> topCountries,
        List<CityStats> topCities,
        List<DeviceStats> deviceBreakdown
) {}