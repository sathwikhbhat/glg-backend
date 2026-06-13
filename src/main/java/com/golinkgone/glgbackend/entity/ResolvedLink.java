package com.golinkgone.glgbackend.entity;

import java.util.UUID;

public record ResolvedLink(UUID linkId, String originalUrl, boolean hasOwner) {}
