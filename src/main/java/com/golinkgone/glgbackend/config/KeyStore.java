package com.golinkgone.glgbackend.config;

import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class KeyStore {
    private final Set<String> keySet = ConcurrentHashMap.newKeySet();
    private final WebsiteUrlRepository urlRepository;

    public KeyStore(WebsiteUrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @PostConstruct
    public void loadKeys() {
        List<String> keys = urlRepository.findAllShortKeys();
        keySet.addAll(keys);
    }

    public void addKey(String key) {
        keySet.add(key);
    }

    public boolean contains(String key) {
        return keySet.contains(key);
    }

    public void removeKey(String key) {
        keySet.remove(key);
    }
}
