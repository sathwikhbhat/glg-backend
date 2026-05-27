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
@Table(name = "link_device_stats")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkDeviceStats {

    @EmbeddedId
    private LinkDeviceStatsId id;

    @Column(name = "total_clicks", nullable = false)
    private Long totalClicks;

    @Column(name = "new_visitors", nullable = false)
    private Long newVisitors;

    public LinkDeviceStats(UUID linkId, String deviceType, long totalClicks, long newVisitors) {
        this.id = new LinkDeviceStatsId(linkId, deviceType);
        this.totalClicks = totalClicks;
        this.newVisitors = newVisitors;
    }
}
