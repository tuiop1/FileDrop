package dev.tuiop.filedrop.drop.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EncryptionMetadataEntity {

    @Column(name = "encrypted_data_key", nullable = false)
    private byte[] encryptedDataKey;

    @Column(name = "file_iv", nullable = false)
    private byte[] fileIv;

    @Column(name = "key_iv", nullable = false)
    private byte[] keyIv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;
}
