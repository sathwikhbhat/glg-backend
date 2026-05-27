package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import com.golinkgone.glgbackend.entity.DeviceType;
import com.golinkgone.glgbackend.entity.GeoLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickIngestionServiceTest {

    private static final String IPHONE_UA  = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";
    private static final String IPAD_UA    = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
    private static final String ANDROID_PHONE_UA  = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Mobile) AppleWebKit/537.36";
    private static final String ANDROID_TABLET_UA = "Mozilla/5.0 (Linux; Android 14; SM-X910) AppleWebKit/537.36";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String BOT_UA     = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    @Mock GeoIPService geoIPService;
    @Mock ClickIngestionQueue queue;
    @InjectMocks ClickIngestionService service;

    @Test
    void classifyDevice_clientHintMobileWins() {
        assertThat(service.classifyDevice(DESKTOP_UA, "?1")).isEqualTo(DeviceType.PHONE);
    }

    @Test
    void classifyDevice_iPhoneUaIsPhone() {
        assertThat(service.classifyDevice(IPHONE_UA, null)).isEqualTo(DeviceType.PHONE);
    }

    @Test
    void classifyDevice_androidPhoneIsPhone() {
        assertThat(service.classifyDevice(ANDROID_PHONE_UA, null)).isEqualTo(DeviceType.PHONE);
    }

    @Test
    void classifyDevice_iPadIsTablet() {
        assertThat(service.classifyDevice(IPAD_UA, null)).isEqualTo(DeviceType.TABLET);
    }

    @Test
    void classifyDevice_androidWithoutMobileIsTablet() {
        assertThat(service.classifyDevice(ANDROID_TABLET_UA, null)).isEqualTo(DeviceType.TABLET);
    }

    @Test
    void classifyDevice_desktopUa() {
        assertThat(service.classifyDevice(DESKTOP_UA, null)).isEqualTo(DeviceType.DESKTOP);
    }

    @Test
    void classifyDevice_blankUa_isUnknown() {
        assertThat(service.classifyDevice("", null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(service.classifyDevice(null, null)).isEqualTo(DeviceType.UNKNOWN);
    }

    @Test
    void isBot_detectsCommonCrawlers() {
        assertThat(service.isBot(BOT_UA)).isTrue();
        assertThat(service.isBot("curl/8.4.0")).isTrue();
        assertThat(service.isBot("python-requests/2.31.0")).isTrue();
        assertThat(service.isBot("Java/17")).isTrue();
        assertThat(service.isBot("HeadlessChrome/120")).isTrue();
    }

    @Test
    void isBot_treatsBlankAsBot() {
        assertThat(service.isBot("")).isTrue();
        assertThat(service.isBot(null)).isTrue();
    }

    @Test
    void isBot_normalBrowserIsNotBot() {
        assertThat(service.isBot(DESKTOP_UA)).isFalse();
        assertThat(service.isBot(IPHONE_UA)).isFalse();
    }

    @Test
    void enqueueClick_skipsBlankIp() {
        service.enqueueClick(UUID.randomUUID(), "", DESKTOP_UA, null);
        verify(queue, never()).offer(any());
    }

    @Test
    void enqueueClick_skipsBot() {
        service.enqueueClick(UUID.randomUUID(), "1.2.3.4", BOT_UA, null);
        verify(queue, never()).offer(any());
    }

    @Test
    void enqueueClick_pushesEnrichedDtoToQueue() {
        UUID linkId = UUID.randomUUID();
        when(geoIPService.lookup("1.2.3.4")).thenReturn(new GeoLocation("AS", "IN", "KA", "Bengaluru"));

        service.enqueueClick(linkId, "1.2.3.4", IPHONE_UA, null);

        ArgumentCaptor<ClickEventDTO> captor = ArgumentCaptor.forClass(ClickEventDTO.class);
        verify(queue).offer(captor.capture());
        ClickEventDTO dto = captor.getValue();
        assertThat(dto.linkId()).isEqualTo(linkId);
        assertThat(dto.countryCode()).isEqualTo("IN");
        assertThat(dto.cityName()).isEqualTo("Bengaluru");
        assertThat(dto.deviceType()).isEqualTo(DeviceType.PHONE);
        assertThat(dto.visitorHash()).isNotNull();
    }

    @Test
    void visitorHash_isStable_acrossInvocations() {
        UUID linkId = UUID.randomUUID();
        when(geoIPService.lookup("1.2.3.4")).thenReturn(new GeoLocation("AS", "IN", "KA", "Bengaluru"));

        service.enqueueClick(linkId, "1.2.3.4", IPHONE_UA, null);
        service.enqueueClick(linkId, "1.2.3.4", IPHONE_UA, null);

        ArgumentCaptor<ClickEventDTO> captor = ArgumentCaptor.forClass(ClickEventDTO.class);
        verify(queue, org.mockito.Mockito.times(2)).offer(captor.capture());
        assertThat(captor.getAllValues().get(0).visitorHash())
                .isEqualTo(captor.getAllValues().get(1).visitorHash());
    }

    @Test
    void visitorHash_differs_acrossDifferentIps() {
        UUID linkId = UUID.randomUUID();
        when(geoIPService.lookup(any())).thenReturn(new GeoLocation("AS", "IN", "KA", "Bengaluru"));

        service.enqueueClick(linkId, "1.2.3.4", IPHONE_UA, null);
        service.enqueueClick(linkId, "5.6.7.8", IPHONE_UA, null);

        ArgumentCaptor<ClickEventDTO> captor = ArgumentCaptor.forClass(ClickEventDTO.class);
        verify(queue, org.mockito.Mockito.times(2)).offer(captor.capture());
        assertThat(captor.getAllValues().get(0).visitorHash())
                .isNotEqualTo(captor.getAllValues().get(1).visitorHash());
    }
}
