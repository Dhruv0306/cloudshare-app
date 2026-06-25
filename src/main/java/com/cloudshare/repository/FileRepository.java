package com.cloudshare.repository;

import com.cloudshare.model.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileMetadata, UUID> {
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedFalse(UUID id, UUID ownerId);
    Page<FileMetadata> findByOwnerIdAndDeletedFalse(UUID ownerId, Pageable pageable);
}
