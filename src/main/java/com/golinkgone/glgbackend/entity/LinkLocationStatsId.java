package com.golinkgone.glgbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkLocationStatsId implements Serializable {

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "country_code", nullable = false, length = 8)
    private String countryCode;

    @Column(name = "city_name", nullable = false, length = 128)
    private String cityName;
}
