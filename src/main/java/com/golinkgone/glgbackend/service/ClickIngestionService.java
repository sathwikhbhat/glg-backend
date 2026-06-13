package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ClickEventDTO;
import com.golinkgone.glgbackend.entity.DeviceType;
import com.golinkgone.glgbackend.entity.GeoLocation;
import com.google.common.hash.Hashing;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ClickIngestionService {

    private final GeoIPService geoIPService;
    private final ClickIngestionQueue queue;

    public ClickIngestionService(GeoIPService geoIPService, ClickIngestionQueue queue) {
        this.geoIPService = geoIPService;
        this.queue = queue;
    }

    @Async("analyticsExecutor")
    public void enqueueClick(UUID linkId, String rawIp, String userAgent, String secChUaMobile) {
        try {
            if (!StringUtils.hasText(rawIp)) {
                log.warn("Skipping click for linkId={} — blank IP", linkId);
                return;
            }
            if (isBot(userAgent)) {
                log.debug("Skipping bot click for linkId={}", linkId);
                return;
            }

            UUID visitorHash = computeVisitorHash(rawIp, userAgent);
            GeoLocation loc = geoIPService.lookup(rawIp);
            DeviceType device = classifyDevice(userAgent, secChUaMobile);

            ClickEventDTO dto = new ClickEventDTO(
                    linkId, visitorHash, device, loc.countryCode(), loc.city(), OffsetDateTime.now(ZoneOffset.UTC));
            queue.offer(dto);
        } catch (Exception ex) {
            log.error("Failed to enqueue click for linkId={}", linkId, ex);
        }
    }

    private UUID computeVisitorHash(String rawIp, String userAgent) {
        String input = rawIp + "|" + (userAgent == null ? "" : userAgent);
        byte[] digest =
                Hashing.murmur3_128().hashString(input, StandardCharsets.UTF_8).asBytes();
        ByteBuffer bb = ByteBuffer.wrap(digest);
        long msb = bb.getLong();
        long lsb = bb.getLong();
        return new UUID(msb, lsb);
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
