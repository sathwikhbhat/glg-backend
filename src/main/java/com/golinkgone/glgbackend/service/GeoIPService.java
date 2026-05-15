package com.golinkgone.glgbackend.service;

import com.google.common.net.InetAddresses;
import com.maxmind.db.CHMCache;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CityResponse;
import com.golinkgone.glgbackend.entity.GeoLocation;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;

@Slf4j
@Service
public class GeoIPService {

    private static final GeoLocation UNKNOWN = new GeoLocation("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN");

    @Value("${maxmind.db-path}")
    private Resource geoIPResource;

    private volatile DatabaseReader reader;

    @PostConstruct
    public void init() {
        try {
            File dbFile = materializeDbFile(geoIPResource);
            reader = build(dbFile);
            log.info("MaxMind GeoLite2 database loaded (memory-mapped) from {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to load MaxMind database — geo lookups will return UNKNOWN", e);
        }
    }

    public boolean isReady() {
        return reader != null;
    }

    public void reload(File newDb) throws IOException {
        DatabaseReader fresh = build(newDb);
        DatabaseReader old = this.reader;
        this.reader = fresh;
        if (old != null) {
            try { old.close(); } catch (IOException e) { log.warn("Failed to close old GeoLite2 reader", e); }
        }
        log.info("MaxMind GeoLite2 reader reloaded from {}", newDb.getAbsolutePath());
    }

    private DatabaseReader build(File dbFile) throws IOException {
        return new DatabaseReader.Builder(dbFile)
                .fileMode(Reader.FileMode.MEMORY_MAPPED)
                .withCache(new CHMCache())
                .build();
    }

    public GeoLocation lookup(String ip) {
        if (ip == null || !InetAddresses.isInetAddress(ip)) {
            return UNKNOWN;
        }
        if (reader == null) {
            return UNKNOWN;
        }
        try {
            InetAddress address = InetAddresses.forString(ip);
            CityResponse response = reader.city(address);

            String continent = response.continent().name();
            String country   = response.country().name();
            String region    = response.mostSpecificSubdivision().name();
            String city      = response.city().name();

            return new GeoLocation(
                    continent != null ? continent : "UNKNOWN",
                    country   != null ? country   : "UNKNOWN",
                    region    != null ? region    : "UNKNOWN",
                    city      != null ? city      : "UNKNOWN"
            );
        } catch (AddressNotFoundException e) {
            log.debug("No geo data for IP hash [{}]", ip.hashCode());
            return UNKNOWN;
        } catch (Exception e) {
            log.warn("GeoIP lookup failed for IP hash [{}]: {}", ip.hashCode(), e.getMessage());
            return UNKNOWN;
        }
    }

    private File materializeDbFile(Resource resource) throws IOException {
        if (resource.isFile()) {
            return resource.getFile();
        }
        File tmp = Files.createTempFile("geolite2-", ".mmdb").toFile();
        tmp.deleteOnExit();
        try (InputStream in = resource.getInputStream();
             FileOutputStream out = new FileOutputStream(tmp)) {
            StreamUtils.copy(in, out);
        }
        return tmp;
    }
}