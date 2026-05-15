package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @Mock WebsiteUrlRepository repository;
    @InjectMocks OwnerService ownerService;

    @Test
    void isOwner_returnsTrue_whenRepositoryConfirms() {
        UUID userId = UUID.randomUUID();
        when(repository.existsByShortKeyAndUserId("abc123", userId)).thenReturn(true);

        assertThat(ownerService.isOwner("abc123", userId)).isTrue();
    }

    @Test
    void isOwner_returnsFalse_whenRepositoryDenies() {
        UUID userId = UUID.randomUUID();
        when(repository.existsByShortKeyAndUserId("abc123", userId)).thenReturn(false);

        assertThat(ownerService.isOwner("abc123", userId)).isFalse();
    }
}
