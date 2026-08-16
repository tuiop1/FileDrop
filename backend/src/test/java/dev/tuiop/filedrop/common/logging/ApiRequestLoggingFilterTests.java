package dev.tuiop.filedrop.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApiRequestLoggingFilterTests {

    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesValidRequestIdAndMakesItAvailableDuringTheRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/drops/123");
        request.setServletPath("/api/v1/drops/123");
        request.addHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER, "client-request-123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY))
                    .isEqualTo("client-request-123");
            servletRequest.setAttribute(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/api/v1/drops/{id}"
            );
            ((MockHttpServletResponse) servletResponse).setStatus(200);
        });

        assertThat(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER))
                .isEqualTo("client-request-123");
        assertThat(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/drops/123");
        request.setServletPath("/api/v1/drops/123");
        request.addHeader(
                ApiRequestLoggingFilter.REQUEST_ID_HEADER,
                "invalid request id\nforged-log-entry"
        );
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String requestId = response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotNull();
        assertThatCode(() -> UUID.fromString(requestId)).doesNotThrowAnyException();
    }

    @Test
    void ignoresNonApiRequests() throws Exception {
        var request = new MockHttpServletRequest("GET", "/index.html");
        request.setServletPath("/index.html");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER)).isNull();
    }
}
