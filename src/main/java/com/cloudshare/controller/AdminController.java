package com.cloudshare.controller;

import com.cloudshare.dto.AdminUserResponse;
import com.cloudshare.dto.ApiResponse;
import com.cloudshare.model.AuditLog;
import com.cloudshare.repository.UserRepository;
import com.cloudshare.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminUserResponse> response = userRepository.findAll(pageable)
                .map(user -> AdminUserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .roles(user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toList()))
                        .mfaEnabled(user.isMfaEnabled())
                        .createdAt(user.getCreatedAt())
                        .build());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAuditLogs(userId, action, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
