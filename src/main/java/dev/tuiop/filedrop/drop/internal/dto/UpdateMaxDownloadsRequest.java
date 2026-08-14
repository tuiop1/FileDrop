package dev.tuiop.filedrop.drop.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateMaxDownloadsRequest(
        @NotNull
        @Min(1)
        @Max(100)
        Integer maxDownloads
) {
}
