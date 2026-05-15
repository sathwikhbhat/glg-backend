package com.shreyasnandurkar.idresolutionsystem.service;

import com.shreyasnandurkar.idresolutionsystem.entity.ClickEvent;
import com.shreyasnandurkar.idresolutionsystem.entity.DeviceType;
import com.shreyasnandurkar.idresolutionsystem.entity.GeoLocation;
import com.shreyasnandurkar.idresolutionsystem.repository.ClickEventRepository;
import com.shreyasnandurkar.idresolutionsystem.repository.VisitorFingerprintRepository;

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