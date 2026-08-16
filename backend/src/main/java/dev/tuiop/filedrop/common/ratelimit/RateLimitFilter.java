package dev.tuiop.filedrop.common.ratelimit;

import dev.tuiop.filedrop.common.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {


    private static final String REQUEST_SCOPE = "request";
    private static final String UPLOAD_SCOPE = "upload";
    private static final String UPLOAD_PATH =
            "/api/v1/drops";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String clientId = getClientId(request);

        if (!allowGeneralRequest(clientId)) {
            reject(response);
            return;
        }

        if(isUpload(request) && !allowUpload(clientId)){
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);


    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(
                HttpStatus.TOO_MANY_REQUESTS.value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.getWriter().write("""
                {
                  "code": "RATE_LIMIT_EXCEEDED",
                  "message": "Too many requests"
                }
                """);
    }

    private String getClientId(HttpServletRequest request) {
        return request.getRemoteAddr();

    }

    private boolean allowUpload(String clientId) {
        RateLimitProperties.Policy policy =
                properties.upload();

        return rateLimiter.allow(
                UPLOAD_SCOPE,
                clientId,
                policy.limit(),
                policy.window()
        );
    }


    private boolean isUpload(
            HttpServletRequest request
    ) {
        return HttpMethod.POST.matches(request.getMethod())
                && UPLOAD_PATH.equals(request.getServletPath());
    }

    private boolean allowGeneralRequest(String clientId) {
        RateLimitProperties.Policy policy =
                properties.request();

        return rateLimiter.allow(
                REQUEST_SCOPE,
                clientId,
                policy.limit(),
                policy.window()
        );
    }


    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return !request.getServletPath()
                .startsWith("/api/");
    }
}
