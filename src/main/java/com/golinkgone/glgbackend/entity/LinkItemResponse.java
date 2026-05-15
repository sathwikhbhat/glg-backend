package com.golinkgone.glgbackend.entity;

import java.time.OffsetDateTime;

public record LinkItemResponse (
    String shortKey,
    String shortUrl,
    String originalUrl,
    OffsetDateTime createdAt
) {}
