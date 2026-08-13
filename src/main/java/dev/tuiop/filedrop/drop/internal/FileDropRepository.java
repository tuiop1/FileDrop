package dev.tuiop.filedrop.drop.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface FileDropRepository extends JpaRepository<FileDrop, UUID> {

    Optional<FileDrop> findByDownloadTokenHash(String downloadTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select fileDrop
            from FileDrop fileDrop
            where fileDrop.downloadTokenHash = :downloadTokenHash
            """)
    Optional<FileDrop> findByDownloadTokenHashForUpdate(
            @Param("downloadTokenHash") String downloadTokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FileDrop> findByIdAndManagementTokenHashForUpdate(UUID id, String managementTokenHash);


    @Query("""
    select f.id
    from FileDrop f
    where f.status = DELETION_PENDING
       or f.status = FAILED
       or (
           f.status = PENDING
           and f.createdAt <= :stalePendingBefore
       )
       or (
           f.status = AVAILABLE
           and (
               f.expiresAt <= :now
               or f.downloadCount >= f.maxDownloads
           )
       )
       order by f.createdAt asc
""")
    List<UUID> findCleanupCandidatesIds(
            @Param("now") Instant now,
            @Param("stalePendingBefore") Instant stalePendingBefore,
            Limit limit
    );
}
