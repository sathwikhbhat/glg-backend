package com.golinkgone.glgbackend.repository;

import com.golinkgone.glgbackend.service.ClickAggregationWriter.CountIncrement;
import com.golinkgone.glgbackend.service.ClickAggregationWriter.UniqueVisitorRow;
import com.golinkgone.glgbackend.service.ClickAggregationWriter.LogRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Warning for Sathwik O_o:- these batches rely on per-row return codes from
 * {@code int[]} to detect new-visitor inserts. If anyone enables the JDBC
 * driver flag {@code reWriteBatchedInserts=true} (Postgres driver),
 * batches are collapsed into a single multi-VALUES INSERT and per-row
 * counts collapse into one aggregate count. New-visitor accounting then
 * silently breaks. DO NOT enable that flag globally.
 */
@Slf4j
@Repository
public class ClickAggregationRepository {

    private static final String SQL_INSERT_UV_GLOBAL = """
            INSERT INTO unique_visitors_global (link_id, visitor_hash)
            VALUES (?, ?)
            ON CONFLICT (link_id, visitor_hash) DO NOTHING
            """;

    private static final String SQL_INSERT_UV_LOG = """
            INSERT INTO unique_visitors_log (link_id, click_time, visitor_hash, is_new_visitor)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (link_id, click_time, visitor_hash) DO NOTHING
            """;

    private static final String SQL_UPSERT_LINK_GLOBAL = """
            INSERT INTO link_stats_global (link_id, total_clicks, new_visitors)
            VALUES (?, ?, ?)
            ON CONFLICT (link_id) DO UPDATE SET
                total_clicks = link_stats_global.total_clicks + EXCLUDED.total_clicks,
                new_visitors = link_stats_global.new_visitors + EXCLUDED.new_visitors
            """;

    private static final String SQL_UPSERT_LINK_MONTHLY = """
            INSERT INTO link_stats_monthly (link_id, bucket_month, total_clicks, new_visitors)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (link_id, bucket_month) DO UPDATE SET
                total_clicks = link_stats_monthly.total_clicks + EXCLUDED.total_clicks,
                new_visitors = link_stats_monthly.new_visitors + EXCLUDED.new_visitors
            """;

    private static final String SQL_UPSERT_DEVICE = """
            INSERT INTO link_device_stats (link_id, device_type, total_clicks, new_visitors)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (link_id, device_type) DO UPDATE SET
                total_clicks = link_device_stats.total_clicks + EXCLUDED.total_clicks,
                new_visitors = link_device_stats.new_visitors + EXCLUDED.new_visitors
            """;

    private static final String SQL_UPSERT_LOCATION = """
            INSERT INTO link_location_stats (link_id, country_code, city_name, total_clicks, new_visitors)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (link_id, country_code, city_name) DO UPDATE SET
                total_clicks = link_location_stats.total_clicks + EXCLUDED.total_clicks,
                new_visitors = link_location_stats.new_visitors + EXCLUDED.new_visitors
            """;

    private final JdbcTemplate jdbc;

    public ClickAggregationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the raw {@code int[]} from {@link JdbcTemplate#batchUpdate} —
     * one entry per row. A value of {@code 1} means the row was inserted
     * (i.e. the visitor was new for this link); {@code 0} means a conflict
     * (the visitor already existed). Any other value is unexpected and
     * indicates batch rewriting may be enabled — the caller logs and
     * conservatively treats those rows as not-new.
     */
    public int[] batchInsertUniqueVisitorsGlobal(List<UniqueVisitorRow> rows) {
        if (rows.isEmpty()) return new int[0];
        return jdbc.batchUpdate(SQL_INSERT_UV_GLOBAL, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                UniqueVisitorRow r = rows.get(i);
                ps.setObject(1, r.linkId(), Types.OTHER);
                ps.setObject(2, r.visitorHash(), Types.OTHER);
            }
            @Override public int getBatchSize() { return rows.size(); }
        });
    }

    public void batchInsertUniqueVisitorsLog(List<LogRow> rows) {
        if (rows.isEmpty()) return;
        jdbc.batchUpdate(SQL_INSERT_UV_LOG, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogRow r = rows.get(i);
                ps.setObject(1, r.linkId(), Types.OTHER);
                ps.setTimestamp(2, Timestamp.from(r.clickTime().toInstant()));
                ps.setObject(3, r.visitorHash(), Types.OTHER);
                ps.setBoolean(4, r.isNewVisitor());
            }
            @Override public int getBatchSize() { return rows.size(); }
        });
    }

    public void batchUpsertLinkStatsGlobal(Map<UUID, CountIncrement> increments) {
        if (increments.isEmpty()) return;
        List<Map.Entry<UUID, CountIncrement>> entries = List.copyOf(increments.entrySet());
        jdbc.batchUpdate(SQL_UPSERT_LINK_GLOBAL, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map.Entry<UUID, CountIncrement> e = entries.get(i);
                ps.setObject(1, e.getKey(), Types.OTHER);
                ps.setLong(2, e.getValue().totalClicks());
                ps.setLong(3, e.getValue().newVisitors());
            }
            @Override public int getBatchSize() { return entries.size(); }
        });
    }

    public void batchUpsertLinkStatsMonthly(Map<MonthlyKey, CountIncrement> increments) {
        if (increments.isEmpty()) return;
        List<Map.Entry<MonthlyKey, CountIncrement>> entries = List.copyOf(increments.entrySet());
        jdbc.batchUpdate(SQL_UPSERT_LINK_MONTHLY, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map.Entry<MonthlyKey, CountIncrement> e = entries.get(i);
                ps.setObject(1, e.getKey().linkId(), Types.OTHER);
                ps.setObject(2, e.getKey().bucketMonth(), Types.DATE);
                ps.setLong(3, e.getValue().totalClicks());
                ps.setLong(4, e.getValue().newVisitors());
            }
            @Override public int getBatchSize() { return entries.size(); }
        });
    }

    public void batchUpsertLinkDeviceStats(Map<DeviceKey, CountIncrement> increments) {
        if (increments.isEmpty()) return;
        List<Map.Entry<DeviceKey, CountIncrement>> entries = List.copyOf(increments.entrySet());
        jdbc.batchUpdate(SQL_UPSERT_DEVICE, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map.Entry<DeviceKey, CountIncrement> e = entries.get(i);
                ps.setObject(1, e.getKey().linkId(), Types.OTHER);
                ps.setString(2, e.getKey().deviceType());
                ps.setLong(3, e.getValue().totalClicks());
                ps.setLong(4, e.getValue().newVisitors());
            }
            @Override public int getBatchSize() { return entries.size(); }
        });
    }

    public void batchUpsertLinkLocationStats(Map<LocationKey, CountIncrement> increments) {
        if (increments.isEmpty()) return;
        List<Map.Entry<LocationKey, CountIncrement>> entries = List.copyOf(increments.entrySet());
        jdbc.batchUpdate(SQL_UPSERT_LOCATION, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map.Entry<LocationKey, CountIncrement> e = entries.get(i);
                ps.setObject(1, e.getKey().linkId(), Types.OTHER);
                ps.setString(2, e.getKey().countryCode());
                ps.setString(3, e.getKey().cityName());
                ps.setLong(4, e.getValue().totalClicks());
                ps.setLong(5, e.getValue().newVisitors());
            }
            @Override public int getBatchSize() { return entries.size(); }
        });
    }

    public int deleteUniqueVisitorsLogOlderThanDays(int days) {
        return jdbc.update(
                "DELETE FROM unique_visitors_log WHERE click_time < NOW() - make_interval(days => ?)",
                days);
    }

    public record MonthlyKey(UUID linkId, LocalDate bucketMonth) {}
    public record DeviceKey(UUID linkId, String deviceType) {}
    public record LocationKey(UUID linkId, String countryCode, String cityName) {}
}
