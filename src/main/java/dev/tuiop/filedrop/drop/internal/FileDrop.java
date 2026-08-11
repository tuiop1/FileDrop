package dev.tuiop.filedrop.drop.internal;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "file_drops")
public class FileDrop {
    @Id
    private UUID id;

    @Column(name = "original_file_name", nullable = false)
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
    @Column(name = "expires_at" )
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

}

