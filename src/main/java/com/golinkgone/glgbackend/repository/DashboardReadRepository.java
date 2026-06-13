package com.golinkgone.glgbackend.repository;

import com.golinkgone.glgbackend.entity.CityStats;
import com.golinkgone.glgbackend.entity.ClickStats;
import com.golinkgone.glgbackend.entity.CountryStats;
import com.golinkgone.glgbackend.entity.DeviceStats;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * All reads for the dashboard. Each query targets one source-of-truth table:
 * All-time summary → {@code link_stats_global}</li>
 * All-time chart → {@code link_stats_monthly}</li>
 * 24h/7d/30d charts → {@code unique_visitors_log} (totals + new + UV in one scan)<
 * Dimensions → {@code link_device_stats}, {@code link_location_stats}
 */
@Repository
public class DashboardReadRepository {

    private static final Set<String> LOG_GRANULARITIES = Set.of("hour", "day", "week", "month");

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public LifetimeTotals fetchLifetimeTotals(UUID linkId) {
        return jdbc.query(
                "SELECT total_clicks, new_visitors FROM link_stats_global WHERE link_id = :linkId",
                new MapSqlParameterSource("linkId", linkId),
                rs -> rs.next()
                        ? new LifetimeTotals(rs.getLong("total_clicks"), rs.getLong("new_visitors"))
                        : new LifetimeTotals(0L, 0L));
    }

    /**
     * Dynamic-window timeline (24h / 7d / 30d). All three metrics derive
     * from {@code unique_visitors_log} so totals/new/unique align perfectly
     * with the user's local timezone — even for half-hour offsets (IST).
     */
    public List<ClickStats> fetchLogTimeline(
            UUID linkId, OffsetDateTime from, OffsetDateTime to, String granularity, String tz) {
        if (!LOG_GRANULARITIES.contains(granularity)) {
            throw new IllegalArgumentException("Unsupported granularity: " + granularity);
        }
        String sql = """
                WITH local AS (
                  SELECT DATE_TRUNC('%1$s', click_time AT TIME ZONE :tz) AS bucket_local,
                         visitor_hash,
                         is_new_visitor
                  FROM unique_visitors_log
                  WHERE link_id = :linkId
                    AND click_time >= :from
                    AND click_time <  :to
                ),
                aggregates AS (
                  SELECT bucket_local,
                         COUNT(*)                                  AS total_clicks,
                         COUNT(*) FILTER (WHERE is_new_visitor)    AS new_visitors,
                         COUNT(DISTINCT visitor_hash)              AS unique_visitors
                  FROM local
                  GROUP BY bucket_local
                ),
                bounds AS (
                  SELECT DATE_TRUNC('%1$s', (:from)::timestamptz AT TIME ZONE :tz) AS series_start,
                         DATE_TRUNC('%1$s', (:to)::timestamptz   AT TIME ZONE :tz) AS series_end
                ),
                buckets AS (
                  SELECT generate_series(series_start, series_end, INTERVAL '1 %1$s') AS bucket_local
                  FROM bounds
                )
                SELECT b.bucket_local                          AS bucket_local,
                       COALESCE(a.total_clicks,    0)::bigint  AS total_clicks,
                       COALESCE(a.new_visitors,    0)::bigint  AS new_visitors,
                       COALESCE(a.unique_visitors, 0)::bigint  AS unique_visitors
                FROM buckets b
                LEFT JOIN aggregates a ON a.bucket_local = b.bucket_local
                ORDER BY b.bucket_local
                """.formatted(granularity);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("linkId", linkId)
                .addValue("from", Timestamp.from(from.toInstant()))
                .addValue("to", Timestamp.from(to.toInstant()))
                .addValue("tz", tz);

        return jdbc.query(sql, params, (rs, n) -> {
            Timestamp ts = rs.getTimestamp("bucket_local");
            return new ClickStats(
                    ts.toLocalDateTime().atOffset(ZoneOffset.UTC),
                    rs.getLong("total_clicks"),
                    rs.getLong("new_visitors"),
                    readNullableLong(rs, "unique_visitors"));
        });
    }

    //     All-time monthly timeline. UTC bucket months, zero-filled from the
    //     month the link was created (via {@code website_url.created_at}) through
    //     the current calendar month.
    public List<ClickStats> fetchMonthlyTimeline(UUID linkId) {
        String sql = """
                WITH bounds AS (
                  SELECT DATE_TRUNC('month', w.created_at AT TIME ZONE 'UTC')::date AS first_month,
                         DATE_TRUNC('month', NOW()        AT TIME ZONE 'UTC')::date AS last_month
                  FROM website_url w
                  WHERE w.link_id = :linkId
                ),
                buckets AS (
                  SELECT generate_series(first_month, last_month, INTERVAL '1 month')::date AS bucket_month
                  FROM bounds
                )
                SELECT b.bucket_month                          AS bucket_month,
                       COALESCE(s.total_clicks, 0)::bigint     AS total_clicks,
                       COALESCE(s.new_visitors, 0)::bigint     AS new_visitors,
                       NULL::bigint                            AS unique_visitors
                FROM buckets b
                LEFT JOIN link_stats_monthly s
                       ON s.link_id = :linkId
                      AND s.bucket_month = b.bucket_month
                ORDER BY b.bucket_month
                """;
        return jdbc.query(sql, new MapSqlParameterSource("linkId", linkId), (rs, n) -> {
            Date date = rs.getDate("bucket_month");
            return new ClickStats(
                    date.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC),
                    rs.getLong("total_clicks"),
                    rs.getLong("new_visitors"),
                    readNullableLong(rs, "unique_visitors"));
        });
    }

    public List<DeviceStats> fetchDeviceBreakdown(UUID linkId) {
        String sql = """
                WITH d AS (
                  SELECT device_type, total_clicks
                  FROM link_device_stats
                  WHERE link_id = :linkId
                )
                SELECT device_type,
                       total_clicks,
                       CASE
                         WHEN SUM(total_clicks) OVER () = 0 THEN 0
                         ELSE ROUND((total_clicks * 100.0) / SUM(total_clicks) OVER (), 2)::float8
                       END AS percentage
                FROM d
                ORDER BY total_clicks DESC
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("linkId", linkId),
                (rs, n) -> new DeviceStats(
                        rs.getString("device_type"), rs.getLong("total_clicks"), rs.getDouble("percentage")));
    }

    /**
     * Country roll-up derived from the unified location table.
     */
    public List<CountryStats> fetchTopCountries(UUID linkId, int limit) {
        return jdbc.query(
                """
                        SELECT country_code,
                               SUM(total_clicks) AS total_clicks,
                               SUM(new_visitors) AS new_visitors
                        FROM link_location_stats
                        WHERE link_id = :linkId
                        GROUP BY country_code
                        ORDER BY total_clicks DESC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource().addValue("linkId", linkId).addValue("limit", limit),
                (rs, n) -> new CountryStats(
                        rs.getString("country_code"), rs.getLong("total_clicks"), rs.getLong("new_visitors")));
    }

    public List<CityStats> fetchTopCities(UUID linkId, int limit) {
        return jdbc.query(
                """
                        SELECT country_code, city_name, total_clicks, new_visitors
                        FROM link_location_stats
                        WHERE link_id = :linkId
                        ORDER BY total_clicks DESC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource().addValue("linkId", linkId).addValue("limit", limit),
                (rs, n) -> new CityStats(
                        rs.getString("city_name"),
                        rs.getString("country_code"),
                        rs.getLong("total_clicks"),
                        rs.getLong("new_visitors")));
    }

    public record LifetimeTotals(long totalClicks, long newVisitors) {}
}
