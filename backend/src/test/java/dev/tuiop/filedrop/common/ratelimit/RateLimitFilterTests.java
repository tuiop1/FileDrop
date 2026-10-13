package dev.tuiop.filedrop.common.ratelimit;

import dev.tuiop.filedrop.common.RateLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTests {

    private static final String CLIENT_IP = "203.0.113.10";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, new RateLimitProperties(
                new RateLimitProperties.Policy(100, WINDOW),
                new RateLimitProperties.Policy(5, WINDOW),
                new RateLimitProperties.Policy(5, WINDOW)
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "HEAD"})
    void downloadUsesGeneralAndDownloadQuotas(String method) throws Exception {
        when(rateLimiter.allow("request", CLIENT_IP, 100, WINDOW)).thenReturn(true);
        when(rateLimiter.allow("download", CLIENT_IP, 5, WINDOW)).thenReturn(true);
        var request = request(method, "/api/v1/drops/d/token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).allow("request", CLIENT_IP, 100, WINDOW);
        verify(rateLimiter).allow("download", CLIENT_IP, 5, WINDOW);
        verifyNoMoreInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void sixthDownloadIsRejectedAcrossMethodsAndTokens() throws Exception {
        when(rateLimiter.allow("request", CLIENT_IP, 100, WINDOW)).thenReturn(true);
        when(rateLimiter.allow("download", CLIENT_IP, 5, WINDOW))
                .thenReturn(true, true, true, true, true, false);

        for (int attempt = 1; attempt <= 6; attempt++) {
            var request = request(attempt % 2 == 0 ? "POST" : "GET",
                    "/api/v1/drops/d/token-" + attempt);
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            if (attempt <= 5) {
                assertThat(response.getStatus()).isEqualTo(200);
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getContentType()).isEqualTo("application/json");
                assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
            }
        }

        verify(filterChain, times(5)).doFilter(any(), any());
        verify(rateLimiter, times(6)).allow("download", CLIENT_IP, 5, WINDOW);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/drops/id",
            "GET, /api/v1/drops",
            "PATCH, /api/v1/drops/id/max-downloads",
            "DELETE, /api/v1/drops/d/token",
            "GET, /api/v1/drops/d/",
            "GET, /api/v1/drops/d/token/extra"
    })
    void otherApiRequestsDoNotConsumeDownloadQuota(String method, String path) throws Exception {
        when(rateLimiter.allow("request", CLIENT_IP, 100, WINDOW)).thenReturn(true);
        var request = request(method, path);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).allow("request", CLIENT_IP, 100, WINDOW);
        verifyNoMoreInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void uploadsKeepTheirSeparateQuota() throws Exception {
        when(rateLimiter.allow("request", CLIENT_IP, 100, WINDOW)).thenReturn(true);
        when(rateLimiter.allow("upload", CLIENT_IP, 5, WINDOW)).thenReturn(true);
        var request = request("POST", "/api/v1/drops");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).allow("request", CLIENT_IP, 100, WINDOW);
        verify(rateLimiter).allow("upload", CLIENT_IP, 5, WINDOW);
        verifyNoMoreInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void generalRejectionDoesNotConsumeDownloadQuota() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/api/v1/drops/d/token"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(rateLimiter).allow("request", CLIENT_IP, 100, WINDOW);
        verifyNoMoreInteractions(rateLimiter);
        verifyNoInteractions(filterChain);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(CLIENT_IP);
        return request;
    }
}
