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
public class UniqueVisitorGlobalId implements Serializable {

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "visitor_hash", nullable = false)
    private UUID visitorHash;
}
