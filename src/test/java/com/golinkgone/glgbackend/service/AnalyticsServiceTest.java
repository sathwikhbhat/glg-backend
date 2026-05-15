package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.DeviceType;
import com.golinkgone.glgbackend.entity.GeoLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String IPHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";
    private static final String IPAD_UA = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
    private static final String ANDROID_PHONE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Mobile) AppleWebKit/537.36";
    private static final String ANDROID_TABLET_UA = "Mozilla/5.0 (Linux; Android 14; SM-X910) AppleWebKit/537.36";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String BOT_UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    @Mock GeoIPService geoIPService;
    @Mock ClickPersistService clickPersistService;
    @InjectMocks AnalyticsService analyticsService;

    @Test
    void classifyDevice_clientHintMobileWins() {
        assertThat(analyticsService.classifyDevice(DESKTOP_UA, "?1")).isEqualTo(DeviceType.PHONE);
    }

    @Test
    void classifyDevice_iPhoneUaIsPhone() {
        assertThat(analyticsService.classifyDevice(IPHONE_UA, null)).isEqualTo(DeviceType.PHONE);
    }

    @Test
    void classifyDevice_androidPhoneIsPhone() {
        assertThat(analyticsService.classifyDevice(ANDROID_PHONE_UA, null)).isEqualTo(DeviceType.PHONE);
    }

    @Test
    void classifyDevice_iPadIsTablet() {
        assertThat(analyticsService.classifyDevice(IPAD_UA, null)).isEqualTo(DeviceType.TABLET);
    }

    @Test
    void classifyDevice_androidWithoutMobileIsTablet() {
        assertThat(analyticsService.classifyDevice(ANDROID_TABLET_UA, null)).isEqualTo(DeviceType.TABLET);
    }

    @Test
    void classifyDevice_desktopUa() {
        assertThat(analyticsService.classifyDevice(DESKTOP_UA, null)).isEqualTo(DeviceType.DESKTOP);
    }

    @Test
    void classifyDevice_clientHintNotMobile_withUnknownUa_returnsDesktop() {
        assertThat(analyticsService.classifyDevice("Mystery/1.0", "?0")).isEqualTo(DeviceType.DESKTOP);
    }

    @Test
    void classifyDevice_blankUa_isUnknown() {
        assertThat(analyticsService.classifyDevice("", null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(analyticsService.classifyDevice(null, null)).isEqualTo(DeviceType.UNKNOWN);
    }

    @Test
    void isBot_detectsCommonCrawlers() {
        assertThat(analyticsService.isBot(BOT_UA)).isTrue();
        assertThat(analyticsService.isBot("curl/8.4.0")).isTrue();
        assertThat(analyticsService.isBot("python-requests/2.31.0")).isTrue();
        assertThat(analyticsService.isBot("Java/17")).isTrue();
        assertThat(analyticsService.isBot("HeadlessChrome/120")).isTrue();
    }

    @Test
    void isBot_treatsBlankAsBot() {
        assertThat(analyticsService.isBot("")).isTrue();
        assertThat(analyticsService.isBot(null)).isTrue();
    }

    @Test
    void isBot_normalBrowserIsNotBot() {
        assertThat(analyticsService.isBot(DESKTOP_UA)).isFalse();
        assertThat(analyticsService.isBot(IPHONE_UA)).isFalse();
    }

    @Test
    void recordClick_skipsBlankIp() {
        analyticsService.recordClick("abc123", "", DESKTOP_UA, null);

        verify(clickPersistService, never()).persistClick(anyString(), anyString(), any(), any());
    }

    @Test
    void recordClick_skipsBot() {
        analyticsService.recordClick("abc123", "1.2.3.4", BOT_UA, null);

        verify(clickPersistService, never()).persistClick(anyString(), anyString(), any(), any());
    }

    @Test
    void recordClick_persistsClassifiedClick() {
        GeoLocation loc = new GeoLocation("EU", "DE", "BE", "Berlin");
        when(geoIPService.lookup("1.2.3.4")).thenReturn(loc);

        analyticsService.recordClick("abc123", "1.2.3.4", IPHONE_UA, null);

        ArgumentCaptor<DeviceType> deviceCaptor = ArgumentCaptor.forClass(DeviceType.class);
        verify(clickPersistService).persistClick(eq("abc123"), anyString(), eq(loc), deviceCaptor.capture());
        assertThat(deviceCaptor.getValue()).isEqualTo(DeviceType.PHONE);
    }
}
