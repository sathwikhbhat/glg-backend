package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import com.golinkgone.glgbackend.entity.DeviceType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClickIngestionQueueTest {

    @Test
    void drainTo_returnsClicksInInsertionOrder() {
        ClickIngestionQueue q = new ClickIngestionQueue();
        ClickEventDTO a = sample();
        ClickEventDTO b = sample();

        q.offer(a);
        q.offer(b);

        List<ClickEventDTO> drained = new ArrayList<>();
        int n = q.drainTo(drained, 100);
        assertThat(n).isEqualTo(2);
        assertThat(drained).containsExactly(a, b);
        assertThat(q.size()).isZero();
    }

    @Test
    void offer_returnsFalse_andCountsDrop_whenFull() {
        ClickIngestionQueue q = new ClickIngestionQueue();
        for (int i = 0; i < ClickIngestionQueue.CAPACITY; i++) {
            assertThat(q.offer(sample())).isTrue();
        }
        assertThat(q.offer(sample())).isFalse();
        assertThat(q.droppedTotal()).isEqualTo(1L);
    }

    private static ClickEventDTO sample() {
        return new ClickEventDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DeviceType.PHONE,
                "IN",
                "Bengaluru",
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
