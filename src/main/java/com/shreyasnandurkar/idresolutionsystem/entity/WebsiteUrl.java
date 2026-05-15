package com.shreyasnandurkar.idresolutionsystem.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "website_url",
        indexes = {
                @Index(name = "idx_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@NoArgsConstructor
public class WebsiteUrl {

    @Id
    private UUID linkId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @Column(name = "short_key", nullable = false, unique = true, updatable = false)
    private String shortKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public WebsiteUrl(String originalUrl, String shortKey, UUID userId) {
        this.linkId = UuidCreator.getTimeOrderedEpoch();
        this.originalUrl = originalUrl;
        this.shortKey = shortKey;
        this.userId = userId;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
