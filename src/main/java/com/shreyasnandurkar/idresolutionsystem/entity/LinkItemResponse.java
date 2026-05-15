package com.shreyasnandurkar.idresolutionsystem.entity;

import java.time.OffsetDateTime;

public record LinkItemResponse (
    String shortKey,
    String shortUrl,
    String originalUrl,
    OffsetDateTime createdAt
) {}
