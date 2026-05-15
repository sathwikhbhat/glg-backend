package com.shreyasnandurkar.idresolutionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "visitor_fingerprint",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visitor_shortkey_iphash",
                columnNames = {"short_key", "ip_address_hash"}
        ))
@Getter
@NoArgsConstructor
public class VisitorFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visitor_id")
    private Long visitorId;

    @Column(name = "short_key", nullable = false)
    private String shortKey;

    @Column(name = "ip_address_hash", nullable = false)
    private String ipAddressHash;

    public VisitorFingerprint(String shortKey, String ipAddressHash) {
        this.shortKey = shortKey;
        this.ipAddressHash = ipAddressHash;
    }
}
