package com.golinkgone.glgbackend.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "unique_visitors_global")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UniqueVisitorGlobal {

    @EmbeddedId
    private UniqueVisitorGlobalId id;

    public UniqueVisitorGlobal(UUID linkId, UUID visitorHash) {
        this.id = new UniqueVisitorGlobalId(linkId, visitorHash);
    }
}
