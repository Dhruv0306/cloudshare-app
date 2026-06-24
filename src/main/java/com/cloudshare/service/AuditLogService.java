package com.cloudshare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void log(UUID userId, String action, UUID fileId, String ipAddress, String details) {
        log.debug("Writing audit log: user={}, action={}, ip={}", userId, action, ipAddress);
        String sql = "INSERT INTO audit_logs (user_id, action, file_id, ip_address, details, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, 
                userId, 
                action, 
                fileId, 
                ipAddress, 
                details, 
                java.sql.Timestamp.from(Instant.now())
            );
        } catch (Exception e) {
            log.error("Failed to write audit log event {} for user {}", action, userId, e);
            throw new RuntimeException("Audit logging failed, transaction rolled back for security compliance", e);
        }
    }
}
