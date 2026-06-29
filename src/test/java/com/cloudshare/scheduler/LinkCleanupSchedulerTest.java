package com.cloudshare.scheduler;

import com.cloudshare.repository.ShareLinkRepository;
import com.cloudshare.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkCleanupSchedulerTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private AuditLogService auditLogService;

    private LinkCleanupScheduler linkCleanupScheduler;

    @BeforeEach
    void setUp() {
        linkCleanupScheduler = new LinkCleanupScheduler(shareLinkRepository, auditLogService);
    }

    @Test
    void cleanupLinks_noExpiredLinks_doesNotLogAudit() {
        when(shareLinkRepository.deleteByExpiresAtBefore(any(Instant.class)))
                .thenReturn(0);

        linkCleanupScheduler.cleanupLinks();

        verify(shareLinkRepository).deleteByExpiresAtBefore(any(Instant.class));
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void cleanupLinks_expiredLinksDeleted_logsAudit() {
        when(shareLinkRepository.deleteByExpiresAtBefore(any(Instant.class)))
                .thenReturn(5);

        linkCleanupScheduler.cleanupLinks();

        verify(shareLinkRepository).deleteByExpiresAtBefore(any(Instant.class));
        verify(auditLogService).log(
                isNull(),
                eq("LINK_CLEANUP"),
                isNull(),
                eq("system"),
                eq("Bulk purged 5 expired share links")
        );
    }
}
