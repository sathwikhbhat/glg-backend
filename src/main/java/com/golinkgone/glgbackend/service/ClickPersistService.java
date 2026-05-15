package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEvent;
import com.golinkgone.glgbackend.entity.DeviceType;
import com.golinkgone.glgbackend.entity.GeoLocation;
import com.golinkgone.glgbackend.repository.ClickEventRepository;
import com.golinkgone.glgbackend.repository.VisitorFingerprintRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ClickPersistService {

    private final ClickEventRepository clickEventRepository;
    private final VisitorFingerprintRepository fingerprintRepository;
    private final FingerprintCache fingerprintCache;

    public ClickPersistService(ClickEventRepository clickEventRepository,
                               VisitorFingerprintRepository fingerprintRepository,
                               FingerprintCache fingerprintCache) {
        this.clickEventRepository = clickEventRepository;
        this.fingerprintRepository = fingerprintRepository;
        this.fingerprintCache = fingerprintCache;
    }

    @Transactional
    public void persistClick(String shortKey, String ipHash, GeoLocation location, DeviceType deviceType) {
        boolean isNew = resolveFirstSeen(shortKey, ipHash);
        clickEventRepository.save(new ClickEvent(
                shortKey,
                ipHash,
                location.city(),
                location.country(),
                isNew,
                deviceType
        ));
    }

    private boolean resolveFirstSeen(String shortKey, String ipHash) {
        if (fingerprintCache.isKnown(shortKey, ipHash)) {
            return false;
        }
        int inserted = fingerprintRepository.insertIfAbsent(shortKey, ipHash);
        fingerprintCache.markKnown(shortKey, ipHash);
        return inserted == 1;
    }
}