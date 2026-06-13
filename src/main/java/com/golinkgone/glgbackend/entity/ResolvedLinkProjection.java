package com.golinkgone.glgbackend.entity;

import java.util.UUID;

public interface ResolvedLinkProjection {
    UUID getLinkId();

    String getOriginalUrl();

    UUID getUserId();
}
