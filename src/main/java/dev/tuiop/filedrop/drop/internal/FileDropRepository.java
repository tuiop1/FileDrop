package dev.tuiop.filedrop.drop.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
