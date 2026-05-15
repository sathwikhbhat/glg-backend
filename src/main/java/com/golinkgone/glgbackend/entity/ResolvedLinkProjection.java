package com.golinkgone.glgbackend.entity;

import java.util.UUID;

public interface ResolvedLinkProjection {
    String getOriginalUrl();
    UUID getUserId();
}