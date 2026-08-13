package dev.tuiop.filedrop.drop.internal.dto;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record DropDownloadResult(
        String filename,
        String contentType,
        long size,
        int downloadsRemaining,
        StreamingResponseBody body


) {
}
