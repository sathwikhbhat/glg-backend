package com.golinkgone.glgbackend.entity;

import java.time.OffsetDateTime;

public record ClickStats(OffsetDateTime bucket, Long total, Long newVisitors, Long uniqueVisitors) {}
