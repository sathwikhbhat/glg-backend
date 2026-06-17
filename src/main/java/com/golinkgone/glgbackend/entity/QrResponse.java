package com.golinkgone.glgbackend.entity;

import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QrResponse(UUID qrId, String label, JsonNode config, String logoUrl, OffsetDateTime createdAt) {}