package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Producers enqueue and a single scheduled consumer drains in batches.
 */
@Slf4j
@Component
public class ClickIngestionQueue {

    public static final int CAPACITY = 10_000;

    private final BlockingQueue<ClickEventDTO> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicLong droppedCount = new AtomicLong();

    public boolean offer(ClickEventDTO dto) {
        boolean accepted = queue.offer(dto);
        if (!accepted) {
            long dropped = droppedCount.incrementAndGet();
            if ((dropped & (dropped - 1)) == 0) {
                log.warn("Click queue full (cap={}); total dropped clicks so far: {}", CAPACITY, dropped);
            }
        }
        return accepted;
    }

    public int drainTo(List<ClickEventDTO> sink, int maxElements) {
        return queue.drainTo(sink, maxElements);
    }

    public int size() {
        return queue.size();
    }

    public long droppedTotal() {
        return droppedCount.get();
    }
}
