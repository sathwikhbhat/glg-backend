package com.shreyasnandurkar.idresolutionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "click_event", indexes = {
        @Index(name = "idx_click_sk_ca", columnList = "short_key, clicked_at"),
        @Index(name = "idx_click_sk_ca_city", columnList = "short_key, clicked_at, city, country, new_visitor"),
        @Index(name = "idx_click_sk_ca_country", columnList = "short_key, clicked_at, country, new_visitor")
})
@Getter
@NoArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clickId;

    @Column(name = "short_key", nullable = false)
    private String shortKey;

    @Column(name = "clicked_at", nullable = false)
    private OffsetDateTime clickedAt;

    @Column(name = "ip_address_hash")
    private String ipAddressHash;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "new_visitor")
    private boolean newVisitor;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private DeviceType deviceType;

    public ClickEvent(String shortKey, String ipAddressHash, String city, String country, boolean newVisitor,
                      DeviceType deviceType) {
        this.shortKey = shortKey;
        this.clickedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.ipAddressHash = ipAddressHash;
        this.city = city;
        this.country = country;
        this.newVisitor = newVisitor;
        this.deviceType = deviceType;
    }
}