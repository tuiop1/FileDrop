package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.metadata.EncryptionMetadataEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FileDropRepositoryIntegrationTests {

    @Autowired
    private FileDropRepository repository;

    @Test
    void savesAndFindsDropByDownloadToken() {
        FileDrop saved = repository.saveAndFlush(
                drop("saved", FileDropStatus.AVAILABLE, Instant.now().plusSeconds(60))
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getDownloadCount()).isZero();

        FileDrop found = repository.findByDownloadTokenHash("download-saved")
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getOriginalFileName()).isEqualTo("saved.txt");
        assertThat(found.getStatus()).isEqualTo(FileDropStatus.AVAILABLE);
    }

    @Test
    void cleanupQueryReturnsExpiredDropButNotActiveDrop() {
        Instant now = Instant.now();
        FileDrop expiredDrop = repository.saveAndFlush(drop(
                "expired",
                FileDropStatus.AVAILABLE,
                now.minusSeconds(1)
        ));
        FileDrop activeDrop = repository.saveAndFlush(drop(
                "active",
                FileDropStatus.AVAILABLE,
                now.plusSeconds(60)
        ));

        var candidates = repository.findCleanupCandidatesIds(
                now,
                now.minusSeconds(60),
                Limit.of(10)
        );

        assertThat(candidates)
                .contains(expiredDrop.getId())
                .doesNotContain(activeDrop.getId());
    }

    private FileDrop drop(String suffix, FileDropStatus status, Instant expiresAt) {
        return FileDrop.builder()
                .encryptionMetadataEntity(EncryptionMetadataEntity.builder()
                        .encryptedDataKey(new byte[]{1, 2, 3})
                        .fileIv(new byte[]{4, 5, 6})
                        .keyIv(new byte[]{7, 8, 9})
                        .keyVersion(1)
                        .build())
                .originalFileName(suffix + ".txt")
                .detectedContentType("text/plain")
                .size(12L)
                .storageKey("storage-" + suffix)
                .sha256("a".repeat(64))
                .downloadTokenHash("download-" + suffix)
                .managementTokenHash("management-" + suffix)
                .expiresAt(expiresAt)
                .maxDownloads(3)
                .status(status)
                .build();
    }

}
