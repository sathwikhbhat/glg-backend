package com.shreyasnandurkar.idresolutionsystem.service;

import com.google.common.hash.Hashing;
import com.shreyasnandurkar.idresolutionsystem.entity.DeviceType;
import com.shreyasnandurkar.idresolutionsystem.entity.GeoLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class AnalyticsService {
    private final GeoIPService geoIPService;
    private final ClickPersistService clickPersistService;

    public AnalyticsService(GeoIPService geoIPService, ClickPersistService clickPersistService) {
        this.geoIPService = geoIPService;
        this.clickPersistService = clickPersistService;
    }

    @Async("analyticsExecutor")
    public void recordClick(String shortKey, String rawIp, String userAgent, String secChUaMobile) {
        try {
            if (!StringUtils.hasText(rawIp)) {
                log.warn("Skipping click tracking for shortKey={} because IP address is blank", shortKey);
                return;
            }
            if (isBot(userAgent)) {
                log.debug("Skipping bot click for shortKey={}", shortKey);
                return;
            }

            String ipHash = hashIpAddress(rawIp);
            GeoLocation location = geoIPService.lookup(rawIp);
            DeviceType deviceType = classifyDevice(userAgent, secChUaMobile);
            clickPersistService.persistClick(shortKey, ipHash, location, deviceType);
        } catch (Exception ex) {
            log.error("Failed to record click for shortKey={}", shortKey, ex);
        }
    }

    private String hashIpAddress(String rawIp) {
        return Hashing.sha256().hashString(rawIp, StandardCharsets.UTF_8).toString();
    }

    DeviceType classifyDevice(String userAgent, String secChUaMobile) {
        if ("?1".equals(secChUaMobile)) return DeviceType.PHONE;

        if (!StringUtils.hasText(userAgent)) return DeviceType.UNKNOWN;
        String ua = userAgent.toLowerCase();

        if (ua.contains("ipad") || (ua.contains("android") && !ua.contains("mobile"))) {
            return DeviceType.TABLET;
        }

        if (ua.contains("iphone")
                || ua.contains("android")
                || ua.contains("mobi")
                || ua.contains("blackberry")
                || ua.contains("windows phone")
                || ua.contains("kaios")) {
            return DeviceType.PHONE;
        }

        if ("?0".equals(secChUaMobile)) return DeviceType.DESKTOP;

        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux") || ua.contains("cros")) {
            return DeviceType.DESKTOP;
        }

        return DeviceType.UNKNOWN;
    }

    boolean isBot(String userAgent) {
        if (!StringUtils.hasText(userAgent)) return true;
        String ua = userAgent.toLowerCase();
        return ua.contains("bot")
                || ua.contains("spider")
                || ua.contains("crawler")
                || ua.contains("slurp")
                || ua.contains("curl/")
                || ua.contains("wget/")
                || ua.contains("python-requests")
                || ua.contains("python-urllib")
                || ua.contains("java/")
                || ua.contains("okhttp")
                || ua.contains("httpclient")
                || ua.contains("headlesschrome")
                || ua.contains("phantomjs");
    }
}