package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OwnerService {

    private final WebsiteUrlRepository urlRepository;

    public OwnerService(WebsiteUrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Cacheable(value = "ownershipCache", key = "#shortKey + '_' + #userId")
    public boolean isOwner(String shortKey, UUID userId) {
        return urlRepository.existsByShortKeyAndUserId(shortKey, userId);
    }
}
