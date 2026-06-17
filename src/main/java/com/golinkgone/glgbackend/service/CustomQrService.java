package com.golinkgone.glgbackend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.golinkgone.glgbackend.entity.QrCode;
import com.golinkgone.glgbackend.entity.QrConfig;
import com.golinkgone.glgbackend.entity.QrResponse;
import com.golinkgone.glgbackend.exception.ShortKeyNotFoundException;
import com.golinkgone.glgbackend.repository.QrCodeRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomQrService {

    private static final int MAX_QR_PER_LINK = 2;
    private static final int MAX_CONFIG_BYTES = 8 * 1024;

    private final QrCodeRepository qrRepository;
    private final OwnerService ownerService;
    private final ObjectMapper objectMapper;

    public CustomQrService(QrCodeRepository qrRepository, OwnerService ownerService, ObjectMapper objectMapper) {
        this.qrRepository = qrRepository;
        this.ownerService = ownerService;
        this.objectMapper = objectMapper;
    }

    public QrResponse create(String shortKey, UUID userId, String label, QrConfig config) {
        UUID linkId = resolveOwned(shortKey, userId);
        if (qrRepository.countByLinkId(linkId) >= MAX_QR_PER_LINK) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A link can have at most " + MAX_QR_PER_LINK + " QR codes");
        }
        QrCode qr = qrRepository.save(new QrCode(linkId, userId, label, serialize(config), null));
        return toResponse(qr);
    }

    public List<QrResponse> list(String shortKey, UUID userId) {
        UUID linkId = resolveOwned(shortKey, userId);
        return qrRepository.findByLinkIdOrderByCreatedAt(linkId).stream()
                .map(this::toResponse)
                .toList();
    }

    public QrResponse update(UUID qrId, UUID userId, String label, QrConfig config) {
        QrCode qr = qrRepository
                .findByQrIdAndUserId(qrId, userId)
                .orElseThrow(() -> new ShortKeyNotFoundException("QR code not found"));
        qr.update(label, serialize(config), qr.getLogoUrl());
        return toResponse(qrRepository.save(qr));
    }

    public void delete(UUID qrId, UUID userId) {
        QrCode qr = qrRepository
                .findByQrIdAndUserId(qrId, userId)
                .orElseThrow(() -> new ShortKeyNotFoundException("QR code not found"));
        qrRepository.delete(qr);
    }

    private UUID resolveOwned(String shortKey, UUID userId) {
        return ownerService
                .resolveOwnedLinkId(shortKey, userId)
                .orElseThrow(() -> new AccessDeniedException("Access Denied"));
    }

    private String serialize(QrConfig config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("QR config exceeds size limit");
            }
            return json;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid QR config");
        }
    }

    private QrResponse toResponse(QrCode qr) {
        try {
            return new QrResponse(
                    qr.getQrId(),
                    qr.getLabel(),
                    objectMapper.readTree(qr.getConfig()),
                    qr.getLogoUrl(),
                    qr.getCreatedAt());
        } catch (JacksonException e) {
            throw new IllegalStateException("Corrupt QR config", e);
        }
    }
}