package com.cloud.computing.filesharingapp.repository;

import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {
    Optional<FileEntity> findByFileName(String fileName);
    List<FileEntity> findByOwner(User owner);
    Optional<FileEntity> findByIdAndOwner(Long id, User owner);
    Optional<FileEntity> findByFileNameAndOwner(String fileName, User owner);
}