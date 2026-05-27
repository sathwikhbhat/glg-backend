package com.golinkgone.glgbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkStatsMonthlyId implements Serializable {

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "bucket_month", nullable = false)
    private LocalDate bucketMonth;
}
