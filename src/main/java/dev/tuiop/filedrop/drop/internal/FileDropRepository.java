package dev.tuiop.filedrop.drop.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;


@Repository
public interface FileDropRepository extends JpaRepository<FileDrop, UUID> {


}
