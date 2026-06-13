package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OwnerService {

    private final WebsiteUrlRepository urlRepository;

    public OwnerService(WebsiteUrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Cacheable(value = "ownershipCache", key = "#shortKey + '_' + #userId")
    public Optional<UUID> resolveOwnedLinkId(String shortKey, UUID userId) {
        return urlRepository.findLinkIdByShortKeyAndUserId(shortKey, userId);
    }
}
