package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ResolvedLink;
import com.golinkgone.glgbackend.entity.ResolvedLinkProjection;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UrlLookupService {

    private final WebsiteUrlRepository repository;

    public UrlLookupService(WebsiteUrlRepository websiteUrlRepository) {
        this.repository = websiteUrlRepository;
    }

    @Cacheable(value = "urlCache", key = "#shortKey")
    public ResolvedLink resolveUrl(String shortKey) {

        ResolvedLinkProjection projection = repository.findResolvedByShortKey(shortKey);
        if (projection == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid URL");
        return new ResolvedLink(
                projection.getLinkId(),
                projection.getOriginalUrl(),
                projection.getUserId() != null);
    }
}