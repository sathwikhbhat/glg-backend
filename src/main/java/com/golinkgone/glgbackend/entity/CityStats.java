package com.golinkgone.glgbackend.entity;

public record CityStats(
        String city,
        String country,
        Long total,
        Long newVisitors
) {}