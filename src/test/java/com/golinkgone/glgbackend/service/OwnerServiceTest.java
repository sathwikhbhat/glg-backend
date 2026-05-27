package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @Mock WebsiteUrlRepository repository;
    @InjectMocks OwnerService ownerService;

    @Test
    void resolveOwnedLinkId_returnsLinkId_whenRepositoryConfirms() {
        UUID userId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        when(repository.findLinkIdByShortKeyAndUserId("abc123", userId)).thenReturn(Optional.of(linkId));

        assertThat(ownerService.resolveOwnedLinkId("abc123", userId)).contains(linkId);
    }

    @Test
    void resolveOwnedLinkId_returnsEmpty_whenRepositoryDenies() {
        UUID userId = UUID.randomUUID();
        when(repository.findLinkIdByShortKeyAndUserId("abc123", userId)).thenReturn(Optional.empty());

        assertThat(ownerService.resolveOwnedLinkId("abc123", userId)).isEmpty();
    }
}