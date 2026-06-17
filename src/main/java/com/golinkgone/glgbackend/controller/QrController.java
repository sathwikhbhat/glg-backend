package com.golinkgone.glgbackend.controller;

import com.golinkgone.glgbackend.entity.QrResponse;
import com.golinkgone.glgbackend.entity.QrUpsertRequest;
import com.golinkgone.glgbackend.service.CustomQrService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class QrController {

    private final CustomQrService customQrService;

    public QrController(CustomQrService customQrService) {
        this.customQrService = customQrService;
    }

    @PostMapping("/{shortKey}/qr")
    public ResponseEntity<QrResponse> create(
            @PathVariable String shortKey, @Valid @RequestBody QrUpsertRequest request, @AuthenticationPrincipal Jwt jwt) {

        QrResponse response =
                customQrService.create(shortKey, UUID.fromString(jwt.getSubject()), request.label(), request.config());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortKey}/qr")
    public ResponseEntity<List<QrResponse>> list(@PathVariable String shortKey, @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(customQrService.list(shortKey, UUID.fromString(jwt.getSubject())));
    }

    @PutMapping("/qr/{qrId}")
    public ResponseEntity<QrResponse> update(
            @PathVariable UUID qrId, @Valid @RequestBody QrUpsertRequest request, @AuthenticationPrincipal Jwt jwt) {

        QrResponse response =
                customQrService.update(qrId, UUID.fromString(jwt.getSubject()), request.label(), request.config());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/qr/{qrId}")
    public ResponseEntity<Void> delete(@PathVariable UUID qrId, @AuthenticationPrincipal Jwt jwt) {

        customQrService.delete(qrId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}