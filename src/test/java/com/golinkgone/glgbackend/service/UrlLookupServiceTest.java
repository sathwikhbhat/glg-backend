package com.golinkgone.glgbackend.service;

import com.golinkgone.glgbackend.entity.ResolvedLink;
import com.golinkgone.glgbackend.entity.ResolvedLinkProjection;
import com.golinkgone.glgbackend.repository.WebsiteUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlLookupServiceTest {

    @Mock WebsiteUrlRepository repository;
    @InjectMocks UrlLookupService service;

    @Test
    void resolveUrl_returnsResolvedLink_whenProjectionPresent() {
        UUID owner = UUID.randomUUID();
        ResolvedLinkProjection projection = projection("https://example.com", owner);
        when(repository.findResolvedByShortKey("abc123")).thenReturn(projection);

        ResolvedLink resolved = service.resolveUrl("abc123");

        assertThat(resolved.originalUrl()).isEqualTo("https://example.com");
        assertThat(resolved.hasOwner()).isTrue();
    }

    @Test
    void resolveUrl_marksAnonymous_whenUserIdNull() {
        ResolvedLinkProjection projection = projection("https://example.com", null);
        when(repository.findResolvedByShortKey("abc123")).thenReturn(projection);

        ResolvedLink resolved = service.resolveUrl("abc123");

        assertThat(resolved.hasOwner()).isFalse();
    }

    @Test
    void resolveUrl_throwsNotFound_whenProjectionNull() {
        when(repository.findResolvedByShortKey("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.resolveUrl("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    private ResolvedLinkProjection projection(String url, UUID userId) {
        UUID linkId = UUID.randomUUID();
        return new ResolvedLinkProjection() {
            @Override public UUID getLinkId() { return linkId; }
            @Override public String getOriginalUrl() { return url; }
            @Override public UUID getUserId() { return userId; }
        };
    }
}
