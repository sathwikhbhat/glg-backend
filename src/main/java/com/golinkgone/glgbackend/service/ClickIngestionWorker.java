package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the click queue every 3 seconds and hands the batch to the writer.
 */
@Slf4j
@Component
public class ClickIngestionWorker {

    private static final int MAX_DRAIN = ClickIngestionQueue.CAPACITY;

    private final ClickIngestionQueue queue;
    private final ClickAggregationWriter writer;

    public ClickIngestionWorker(ClickIngestionQueue queue, ClickAggregationWriter writer) {
        this.queue = queue;
        this.writer = writer;
    }

    @Scheduled(fixedDelay = 3000)
    public void flush() {
        if (queue.size() == 0) return;

        List<ClickEventDTO> drained = new ArrayList<>(Math.min(queue.size(), MAX_DRAIN));
        int drainedCount = queue.drainTo(drained, MAX_DRAIN);
        if (drainedCount == 0) return;

        long start = System.nanoTime();
        try {
            writer.writeBatch(drained);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMs > 1000) {
                log.warn(
                        "Click batch write took {} ms for {} clicks — approaching the 3s flush interval",
                        elapsedMs,
                        drainedCount);
            }
        } catch (Exception ex) {
            // Swallow so the scheduler keeps firing. A single bad batch must not stop the ingestion forever
            log.error("Click batch write failed ({} clicks dropped from this tick)", drainedCount, ex);
        }
    }

    // Final drain on JVM shutdown
    @PreDestroy
    public void flushOnShutdown() {
        int pending = queue.size();
        if (pending == 0) return;

        List<ClickEventDTO> drained = new ArrayList<>(pending);
        int drainedCount = queue.drainTo(drained, pending);
        if (drainedCount == 0) return;

        try {
            writer.writeBatch(drained);
            log.info("Shutdown flush: persisted {} pending clicks", drainedCount);
        } catch (Exception ex) {
            log.error("Shutdown flush failed; {} clicks lost", drainedCount, ex);
        }
    }
}
