package dev.tuiop.filedrop.drop.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_drops")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FileDrop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;


    @Column(name = "detected_content_type", nullable = false)
    private String detectedContentType;

    @Column(name = "size", nullable = false )
    private Long size;

    @Column(name = "storage_key", nullable = false, unique = true )
    private String storageKey;

    @Column(name = "sha256", nullable = false )
    private String sha256;

    @Column(name = "download_token_hash", nullable = false, unique = true )
    private String downloadTokenHash;
    @Column(name = "management_token_hash", nullable = false, unique = true )
    private String managementTokenHash;


    @Column(name = "password_hash")
    private String passwordHash;


    @Column(name = "created_at" )
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "deleted_at" )
    private Instant deletedAt;


    @Column(name = "max_downloads", nullable = false)
    private Integer maxDownloads;
    @Column(name = "download_count", nullable = false)
    private Integer downloadCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FileDropStatus status;


    @Version
    @Column(name = "version", nullable = false)
    private long version;


    @PrePersist
    private void prePersist(){
        createdAt = Instant.now();

    }


}

