package com.golinkgone.glgbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "unique_visitors_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UniqueVisitorLog {

    @EmbeddedId
    private UniqueVisitorLogId id;

    @Column(name = "is_new_visitor", nullable = false)
    private Boolean isNewVisitor;

    public UniqueVisitorLog(UUID linkId, OffsetDateTime clickTime, UUID visitorHash, boolean isNewVisitor) {
        this.id = new UniqueVisitorLogId(linkId, clickTime, visitorHash);
        this.isNewVisitor = isNewVisitor;
    }
}
