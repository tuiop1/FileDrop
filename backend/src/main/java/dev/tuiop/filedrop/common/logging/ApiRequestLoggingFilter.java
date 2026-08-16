package dev.tuiop.filedrop.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern VALID_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String UNKNOWN_API_ROUTE = "/api/**";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        long startedAt = System.nanoTime();

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            logCompletion(request, response, startedAt);
            restoreRequestId(previousRequestId);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String suppliedRequestId = request.getHeader(REQUEST_ID_HEADER);
        if (suppliedRequestId != null
                && VALID_REQUEST_ID.matcher(suppliedRequestId).matches()) {
            return suppliedRequestId;
        }

        return UUID.randomUUID().toString();
    }

    private void logCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt
    ) {
        long durationNanos = System.nanoTime() - startedAt;
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        String route = resolveRoute(request);

        log.atInfo()
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.route", route)
                .addKeyValue("http.response.status_code", response.getStatus())
                .addKeyValue("event.duration", durationNanos)
                .log(
                        "HTTP {} {} completed with status {} in {} ms",
                        request.getMethod(),
                        route,
                        response.getStatus(),
                        durationMillis
                );
    }

    private String resolveRoute(HttpServletRequest request) {
        Object route = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );
        return route == null ? UNKNOWN_API_ROUTE : route.toString();
    }

    private void restoreRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(REQUEST_ID_MDC_KEY);
            return;
        }

        MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/");
    }
}
