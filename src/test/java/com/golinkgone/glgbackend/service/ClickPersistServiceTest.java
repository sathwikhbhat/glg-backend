package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEvent;
import com.golinkgone.glgbackend.entity.DeviceType;
import com.golinkgone.glgbackend.entity.GeoLocation;
import com.golinkgone.glgbackend.repository.ClickEventRepository;
import com.golinkgone.glgbackend.repository.VisitorFingerprintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickPersistServiceTest {

    private static final GeoLocation LOCATION = new GeoLocation("EU", "DE", "BE", "Berlin");

    @Mock ClickEventRepository clickEventRepository;
    @Mock VisitorFingerprintRepository fingerprintRepository;
    @Mock FingerprintCache fingerprintCache;
    @InjectMocks ClickPersistService service;

    @Test
    void persistClick_marksReturning_whenCacheHit() {
        when(fingerprintCache.isKnown("abc123", "hash-x")).thenReturn(true);

        service.persistClick("abc123", "hash-x", LOCATION, DeviceType.PHONE);

        verify(fingerprintRepository, never()).insertIfAbsent("abc123", "hash-x");
        ClickEvent saved = captureSaved();
        assertThat(saved.isNewVisitor()).isFalse();
    }

    @Test
    void persistClick_marksNew_whenCacheMissAndInsertSucceeds() {
        when(fingerprintCache.isKnown("abc123", "hash-x")).thenReturn(false);
        when(fingerprintRepository.insertIfAbsent("abc123", "hash-x")).thenReturn(1);

        service.persistClick("abc123", "hash-x", LOCATION, DeviceType.PHONE);

        verify(fingerprintCache).markKnown("abc123", "hash-x");
        assertThat(captureSaved().isNewVisitor()).isTrue();
    }

    @Test
    void persistClick_marksReturning_whenCacheMissButRowAlreadyExists() {
        when(fingerprintCache.isKnown("abc123", "hash-x")).thenReturn(false);
        when(fingerprintRepository.insertIfAbsent("abc123", "hash-x")).thenReturn(0);

        service.persistClick("abc123", "hash-x", LOCATION, DeviceType.DESKTOP);

        verify(fingerprintCache).markKnown("abc123", "hash-x");
        assertThat(captureSaved().isNewVisitor()).isFalse();
    }

    @Test
    void persistClick_populatesEventFields() {
        when(fingerprintCache.isKnown("abc123", "hash-x")).thenReturn(true);

        service.persistClick("abc123", "hash-x", LOCATION, DeviceType.TABLET);

        ClickEvent saved = captureSaved();
        assertThat(saved.getShortKey()).isEqualTo("abc123");
        assertThat(saved.getIpAddressHash()).isEqualTo("hash-x");
        assertThat(saved.getCity()).isEqualTo("Berlin");
        assertThat(saved.getCountry()).isEqualTo("DE");
        assertThat(saved.getDeviceType()).isEqualTo(DeviceType.TABLET);
    }

    private ClickEvent captureSaved() {
        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
