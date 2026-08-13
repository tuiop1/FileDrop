package dev.tuiop.filedrop.drop.internal.metadata;

import dev.tuiop.filedrop.crypto.EncryptionMetadata;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EncryptionMetadataMapper {

    EncryptionMetadataEntity toEntity(EncryptionMetadata metadata);

    EncryptionMetadata toRecord(EncryptionMetadataEntity entity);
}
