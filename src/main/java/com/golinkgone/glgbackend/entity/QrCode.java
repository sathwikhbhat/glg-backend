package com.golinkgone.glgbackend.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "qr_code",
        indexes = {@Index(name = "idx_qr_code_link", columnList = "link_id")})
@Getter
@NoArgsConstructor
public class QrCode {

    @Id
    @Column(name = "qr_id")
    private UUID qrId;

    @Column(name = "link_id", nullable = false, updatable = false)
    private UUID linkId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "label")
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false)
    private String config;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public QrCode(UUID linkId, UUID userId, String label, String config, String logoUrl) {
        this.qrId = UuidCreator.getTimeOrderedEpoch();
        this.linkId = linkId;
        this.userId = userId;
        this.label = label;
        this.config = config;
        this.logoUrl = logoUrl;
    }

    public void update(String label, String config, String logoUrl) {
        this.label = label;
        this.config = config;
        this.logoUrl = logoUrl;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
