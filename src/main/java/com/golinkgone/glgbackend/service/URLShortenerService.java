package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ResolvedLink;
import org.springframework.cache.Cache;
import com.google.zxing.WriterException;
import com.golinkgone.glgbackend.config.AppProperties;
import com.golinkgone.glgbackend.config.KeyStore;
import com.golinkgone.glgbackend.entity.CreateResponse;
import com.golinkgone.glgbackend.entity.LinkItemResponse;
import com.golinkgone.glgbackend.entity.WebsiteUrl;
import com.golinkgone.glgbackend.exception.ShortKeyNotFoundException;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Service
@Slf4j
public class URLShortenerService {
    private final AppProperties appProperties;
    private final WebsiteUrlRepository repository;
    private final AnalyticsService analyticsService;
    private final UrlLookupService urlLookupService;
    private final QRCodeService qrService;
    private final KeyStore keyStore;
    private final CacheManager cacheManager;

    public URLShortenerService(AppProperties appProperties, WebsiteUrlRepository repository,
                               AnalyticsService analyticsService, UrlLookupService urlLookupService,
                               KeyStore keyStore, QRCodeService qrService, CacheManager cacheManager) {
        this.appProperties = appProperties;
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.urlLookupService = urlLookupService;
        this.keyStore = keyStore;
        this.qrService = qrService;
        this.cacheManager = cacheManager;
    }

    public CreateResponse createShortLink(String originalUrl, UUID userId) {
        validateUrl(originalUrl);

        String shortKey = null;
        boolean saved = false;

        while (!saved) {
            String candidate = ShortKeyGenerator.generateShortKey();

            if (keyStore.contains(candidate)) continue;
            keyStore.addKey(candidate);

            try {
                repository.saveAndFlush(new WebsiteUrl(originalUrl, candidate, userId));
                shortKey = candidate;
                saved = true;
            } catch (DataIntegrityViolationException e) {
                keyStore.removeKey(candidate);
                log.debug("Short-key collision on '{}', retrying.", candidate);
            } catch (Exception e) {
                keyStore.removeKey(candidate);
                log.error("Error while saving short-key: {}", e.getMessage());
                throw e;
            }
        }
        String shortUrl = appProperties.getBaseUrl() + "/" + shortKey;
        byte[] qrCode = null;
        try {
            qrCode = qrService.generateQrImage(shortUrl, 200, 200);
        } catch (IOException | WriterException e) {
            log.error("Failed to generate QR code for short-key: {}", shortUrl);
        }

        return new CreateResponse(shortUrl, qrCode);
    }

    private void validateUrl(String originalUrl) {
        URI uri;
        try {
            uri = new URI(originalUrl);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Malformed URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("URL must use http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank() || !host.contains(".")) {
            throw new IllegalArgumentException("URL must contain a valid host");
        }
    }

    public String redirectUrl(String shortKey, String ip, String userAgent, String secChUaMobile) {

        if (!StringUtils.hasText(shortKey)) {
            throw new IllegalArgumentException("shortKey must not be blank");
        }

        if (!keyStore.contains(shortKey)) {
            throw new ShortKeyNotFoundException("Invalid Link");
        }

        ResolvedLink resolved = urlLookupService.resolveUrl(shortKey);
        if(resolved.hasOwner()){
            analyticsService.recordClick(shortKey, ip, userAgent, secChUaMobile);
        }

        return resolved.originalUrl();
    }

    public Page<LinkItemResponse> getUserLinks(UUID userId, int page, int size){
        Pageable pageable = createPage(page, size);
        Page<LinkItemResponse> rawLinks = repository.findUserLinks(userId, pageable);

        return rawLinks.map(link -> new LinkItemResponse(
                link.shortKey(),
                appProperties.getBaseUrl() + "/" + link.shortKey(),
                link.originalUrl(),
                link.createdAt()
        ));
    }

    private Pageable createPage(int page, int size){
        return (PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public void deleteLink(String shortKey, UUID userId) {
        int deletedRows = repository.deleteByShortKeyAndUserId(shortKey, userId);

        if (deletedRows > 0) {
            keyStore.removeKey(shortKey);

            Cache urlCache = cacheManager.getCache("urlCache");
            if (urlCache != null) urlCache.evict(shortKey);

            Cache analyticsCache = cacheManager.getCache("dashboardAnalyticsCache");
            if (analyticsCache != null) {
                analyticsCache.evict(shortKey + "_24h");
                analyticsCache.evict(shortKey + "_7d");
                analyticsCache.evict(shortKey + "_30d");
                analyticsCache.evict(shortKey + "_all");
            }

            Cache ownershipCache = cacheManager.getCache("ownershipCache");
            if (ownershipCache != null) {
                ownershipCache.evict(shortKey + "_" + userId);
            }
        }
        else {
            throw new ShortKeyNotFoundException("Link not found or access denied");
        }
    }

}
