package com.golinkgone.glgbackend.config;

import com.golinkgone.glgbackend.service.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock RateLimiter rateLimiter;
    @InjectMocks RateLimitInterceptor interceptor;

    @Test
    void preHandle_passes_whenTokenAvailable() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(rateLimiter.tryAcquire("1.2.3.4")).thenReturn(true);

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertThat(proceed).isTrue();
        assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void preHandle_returns429_whenRateLimited() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(rateLimiter.tryAcquire("1.2.3.4")).thenReturn(false);

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertThat(proceed).isFalse();
        assertThat(resp.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(resp.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(resp.getContentAsString()).contains("Too many requests");
    }
}
