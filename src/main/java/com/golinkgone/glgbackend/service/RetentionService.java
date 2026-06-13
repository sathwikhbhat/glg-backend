package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.repository.ClickAggregationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Daily 2:00 AM UTC cleanup of the rolling visitor log.
 */
@Slf4j
@Service
public class RetentionService {

    static final int RETENTION_DAYS = 35;

    private final ClickAggregationRepository repository;

    public RetentionService(ClickAggregationRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void purgeOldVisitorLogRows() {
        long start = System.currentTimeMillis();
        try {
            int deleted = repository.deleteUniqueVisitorsLogOlderThanDays(RETENTION_DAYS);
            log.info(
                    "Retention sweep: deleted {} unique_visitors_log rows older than {} days in {} ms",
                    deleted,
                    RETENTION_DAYS,
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.error("Retention sweep failed", ex);
        }
    }
}
