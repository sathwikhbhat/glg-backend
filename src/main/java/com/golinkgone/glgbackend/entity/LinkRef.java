package com.golinkgone.glgbackend.entity;

import java.util.UUID;

public record LinkRef(
        String shortKey, 
        UUID linkId
) {}