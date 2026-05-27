package com.golinkgone.glgbackend.entity;

public record GeoLocation(
        String continent,
        String countryCode,
        String regionName,
        String city
) {
}
