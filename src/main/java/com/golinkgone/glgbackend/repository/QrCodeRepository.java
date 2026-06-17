package com.golinkgone.glgbackend.repository;

import com.golinkgone.glgbackend.entity.QrCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    List<QrCode> findByLinkIdOrderByCreatedAt(UUID linkId);

    long countByLinkId(UUID linkId);

    Optional<QrCode> findByQrIdAndUserId(UUID qrId, UUID userId);
}