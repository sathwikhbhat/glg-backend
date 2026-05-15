package com.golinkgone.glgbackend.entity;

import java.util.List;

public record DashboardResponse(
        long totalClicks,
        long uniqueClicks,
        List<ClickStats> totals,
        List<CountryStats> topCountries,
        List<CityStats> topCities
) {
}
