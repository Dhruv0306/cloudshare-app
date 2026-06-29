package com.cloudshare.scheduler;

import com.cloudshare.repository.ShareLinkRepository;
import com.cloudshare.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkCleanupScheduler {

    private final ShareLinkRepository shareLinkRepository;
    private final AuditLogService auditLogService;

    @Scheduled(cron = "${app.scheduler.link-cleanup.cron:0 0 3 * * ?}")
    public void cleanupLinks() {
        log.info("Starting expired link cleanup scheduler job...");
        Instant now = Instant.now();
        
        // This execution runs in its own transaction & commits first.
        int deletedCount = shareLinkRepository.deleteByExpiresAtBefore(now);
        
        // Audit logging runs in its own transaction afterward if delete was successful.
        if (deletedCount > 0) {
            auditLogService.log(
                null,
                "LINK_CLEANUP",
                null,
                "system",
                "Bulk purged " + deletedCount + " expired share links"
            );
            log.info("Successfully deleted {} expired public share links", deletedCount);
        }
        log.info("Finished expired link cleanup scheduler job.");
    }
}
