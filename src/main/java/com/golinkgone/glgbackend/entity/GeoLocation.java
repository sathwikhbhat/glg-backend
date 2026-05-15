package com.golinkgone.glgbackend.entity;

public record GeoLocation(
        String continent,
        String country,
        String regionName,
        String city
) {
}
