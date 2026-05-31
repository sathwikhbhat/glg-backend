package com.golinkgone.glgbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Component
@Slf4j
public class GeoLiteUpdater {

    private static final String DOWNLOAD_URL =
            "https://download.maxmind.com/app/geoip_download?edition_id=%s&suffix=tar.gz&license_key=%s";

    private final GeoIPService geoIPService;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Value("${maxmind.license-key:}")
    private String licenseKey;

    @Value("${maxmind.edition-id:GeoLite2-City}")
    private String editionId;

    @Value("${maxmind.db-path}")
    private Resource dbResource;

    public GeoLiteUpdater(GeoIPService geoIPService) {
        this.geoIPService = geoIPService;
    }

    @Scheduled(cron = "${maxmind.update-cron:0 0 3 * * SUN}", zone = "UTC")
    public void updateDatabase() {
        if (!StringUtils.hasText(licenseKey)) {
            log.info("MAXMIND_LICENSE_KEY not configured; skipping GeoLite2 update");
            return;
        }

        Path archive = null;
        Path mmdb = null;
        try {
            log.info("Starting GeoLite2 update for edition {}", editionId);
            archive = download();
            mmdb = extractMmdb(archive);
            if (dbResource.isFile()) {
                Files.copy(mmdb, dbResource.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
                geoIPService.reload(dbResource.getFile());
            } else {
                geoIPService.reload(mmdb.toFile());
            }
            log.info("GeoLite2 database updated");
        } catch (Exception e) {
            log.error("GeoLite2 update failed; existing database remains active", e);
        } finally {
            deleteQuietly(archive);
            deleteQuietly(mmdb);
        }
    }

    private Path download() throws IOException, InterruptedException {
        URI uri = URI.create(DOWNLOAD_URL.formatted(editionId, licenseKey));
        Path tmp = Files.createTempFile("geolite2-", ".tar.gz");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMinutes(5))
                .build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("MaxMind responded HTTP " + response.statusCode());
        }
        return tmp;
    }

    private Path extractMmdb(Path tarGz) throws IOException {
        try (InputStream fis = Files.newInputStream(tarGz);
             GzipCompressorInputStream gis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tar = new TarArchiveInputStream(gis)) {

            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".mmdb")) {
                    Path out = Files.createTempFile("geolite2-", ".mmdb");
                    Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                    return out;
                }
            }
        }
        throw new IOException(".mmdb entry not found in archive");
    }

    private static void deleteQuietly(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }
}