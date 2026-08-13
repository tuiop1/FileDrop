package dev.tuiop.filedrop.scanning.internal.typedetection;

import dev.tuiop.filedrop.scanning.internal.exception.ContentTypeDetectionException;
import dev.tuiop.filedrop.scanning.internal.exception.UnsupportedContentTypeException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

@Component
class TikaTypeDetector implements ContentTypeDetector {

    private final Tika tika = new Tika();
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            // Documents
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/json",
            "application/xml",

            // Microsoft Office
            "application/msword", // .doc
            "application/vnd.ms-excel", // .xls
            "application/vnd.ms-powerpoint", // .ppt
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx

            // OpenDocument
            "application/vnd.oasis.opendocument.text", // .odt
            "application/vnd.oasis.opendocument.spreadsheet", // .ods
            "application/vnd.oasis.opendocument.presentation", // .odp

            // Images
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",

            // Archives
            "application/zip",
            "application/x-7z-compressed",
            "application/x-rar-compressed",

            // Video
            "video/mp4",
            "video/webm",

            // Audio
            "audio/mpeg",
            "audio/vnd.wave",
            "audio/x-flac"
    );



    @Override
    public String detect(Path path) {
        try {
            String contentType = tika.detect(path);

            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new UnsupportedContentTypeException(contentType);
            }

            return contentType;
        } catch (IOException exception) {
            throw new ContentTypeDetectionException(exception);
        }
    }
}
