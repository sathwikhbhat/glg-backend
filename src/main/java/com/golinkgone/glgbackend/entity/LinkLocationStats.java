package com.golinkgone.glgbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "link_location_stats")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkLocationStats {

    @EmbeddedId
    private LinkLocationStatsId id;

    @Column(name = "total_clicks", nullable = false)
    private Long totalClicks;

    @Column(name = "new_visitors", nullable = false)
    private Long newVisitors;

    public LinkLocationStats(UUID linkId, String countryCode, String cityName,
                             long totalClicks, long newVisitors) {
        this.id = new LinkLocationStatsId(linkId, countryCode, cityName);
        this.totalClicks = totalClicks;
        this.newVisitors = newVisitors;
    }
}
