package com.golinkgone.glgbackend.entity;

import java.util.List;

public record DashboardResponse(
        long totalClicks,
        long newVisitors,
        List<ClickStats> totals,
        List<CountryStats> topCountries,
        List<CityStats> topCities,
        List<DeviceStats> deviceBreakdown
) {}
