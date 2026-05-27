package com.golinkgone.glgbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "link_stats_monthly")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkStatsMonthly {

    @EmbeddedId
    private LinkStatsMonthlyId id;

    @Column(name = "total_clicks", nullable = false)
    private Long totalClicks;

    @Column(name = "new_visitors", nullable = false)
    private Long newVisitors;

    public LinkStatsMonthly(UUID linkId, LocalDate bucketMonth, long totalClicks, long newVisitors) {
        this.id = new LinkStatsMonthlyId(linkId, bucketMonth);
        this.totalClicks = totalClicks;
        this.newVisitors = newVisitors;
    }
}
