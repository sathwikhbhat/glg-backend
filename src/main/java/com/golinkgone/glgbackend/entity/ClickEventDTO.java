package com.golinkgone.glgbackend.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClickEventDTO(
        UUID linkId,
        UUID visitorHash,
        DeviceType deviceType,
        String countryCode,
        String cityName,
        OffsetDateTime clickTime
) {}
