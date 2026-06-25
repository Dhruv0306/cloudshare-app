package com.cloudshare.repository;

import com.cloudshare.model.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {
    Optional<ShareLink> findByShareCode(String shareCode);
    boolean existsByShareCode(String shareCode);
}
