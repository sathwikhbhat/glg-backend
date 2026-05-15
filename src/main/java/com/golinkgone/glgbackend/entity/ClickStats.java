package com.golinkgone.glgbackend.entity;

import java.time.LocalDateTime;

public interface ClickStats {
    LocalDateTime getBucket();

    Long getTotal();

    Long getNewVisitors();
}
