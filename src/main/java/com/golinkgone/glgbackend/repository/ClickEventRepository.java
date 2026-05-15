package com.golinkgone.glgbackend.repository;


import com.golinkgone.glgbackend.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query(value = """
            SELECT DATE_TRUNC(CAST(:granularity AS text), c.clicked_at) AS bucket,
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE c.new_visitor = true) AS new_visitors
            FROM click_event c
            WHERE c.short_key = :shortKey
                AND c.clicked_at >= :from AND c.clicked_at < :to
            GROUP BY bucket
            ORDER BY bucket""", nativeQuery = true)
    List<ClickStats> getTotals(
            @Param("shortKey") String shortKey,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("granularity") String granularity
    );

    @Query(value = """
                SELECT c.country AS country,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE c.new_visitor = true) AS new_visitors
            FROM click_event c
            WHERE c.short_key = :shortKey
              AND c.clicked_at >= :from
              AND c.clicked_at < :to
              AND c.country IS NOT NULL
            GROUP BY c.country
            ORDER BY total DESC
            """, nativeQuery = true)
    List<CountryStats> getTopCountries(
            @Param("shortKey") String shortKey,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query(value = """
            SELECT c.city AS city,
                   c.country AS country,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE c.new_visitor = true) AS new_visitors
            FROM click_event c
            WHERE c.short_key = :shortKey
              AND c.clicked_at >= :from
              AND c.clicked_at < :to
              AND c.city IS NOT NULL
              AND c.country IS NOT NULL
            GROUP BY c.city, c.country
            ORDER BY total DESC
            LIMIT 15
            """, nativeQuery = true)
    List<CityStats> getTopCities(
            @Param("shortKey") String shortKey,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query(value = """
    SELECT
        COUNT(*) AS total_clicks,
        COUNT(DISTINCT c.ip_address_hash) AS unique_clicks
    FROM click_event c
    WHERE c.short_key = :shortKey
    """, nativeQuery = true)
    LifetimeTotals getLifetimeTotals(@Param("shortKey") String shortKey);

}
