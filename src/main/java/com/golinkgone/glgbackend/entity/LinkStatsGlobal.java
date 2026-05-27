package com.golinkgone.glgbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "link_stats_global")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkStatsGlobal {

    @Id
    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "total_clicks", nullable = false)
    private Long totalClicks;

    @Column(name = "new_visitors", nullable = false)
    private Long newVisitors;
}
