package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.dto.FileDropResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileDropMapper {

	FileDropResponse toResponse(FileDrop fileDrop);
}
