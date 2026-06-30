package com.cloudshare.service;

import com.cloudshare.dto.*;
import com.cloudshare.exception.AccessDeniedException;
import com.cloudshare.exception.InvalidSharePasswordException;
import com.cloudshare.exception.ResourceNotFoundException;
import com.cloudshare.model.*;
import com.cloudshare.repository.FileRepository;
import com.cloudshare.repository.FileShareRepository;
import com.cloudshare.repository.ShareLinkRepository;
import com.cloudshare.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ShareService {

    private final FileShareRepository fileShareRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final EncryptionService encryptionService;
    private final StorageService storageService;
    private final StringRedisTemplate cacheRedisTemplate;

    private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.share-base-url:https://cloudshare.app/share/}")
    private String shareBaseUrl;

    public ShareService(
            FileShareRepository fileShareRepository,
            ShareLinkRepository shareLinkRepository,
            FileRepository fileRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            EncryptionService encryptionService,
            StorageService storageService,
            @Qualifier("redisTemplate") StringRedisTemplate cacheRedisTemplate) {
        this.fileShareRepository = fileShareRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.encryptionService = encryptionService;
        this.storageService = storageService;
        this.cacheRedisTemplate = cacheRedisTemplate;
    }

    @Transactional
    public InternalShareResponse shareFileInternally(InternalShareRequest request, UUID ownerId, String ipAddress) {
        log.info("Processing internal share: file={}, owner={}, target={}", request.getFileId(), ownerId, request.getTargetUsernameOrEmail());

        // 1. Fetch and validate file
        FileMetadata file = fileRepository.findByIdAndOwnerIdAndDeletedFalse(request.getFileId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        // 2. Fetch and validate target user
        User targetUser = userRepository.findByUsernameOrEmail(request.getTargetUsernameOrEmail(), request.getTargetUsernameOrEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        if (targetUser.getId().equals(ownerId)) {
            throw new IllegalArgumentException("Cannot share a file with yourself");
        }

        // 3. Parse permission type
        PermissionType permissionType;
        try {
            permissionType = PermissionType.valueOf(request.getPermissionType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid permission type. Must be READ or WRITE.");
        }

        // 4. Check for existing share
        Optional<FileShare> existingShareOpt = fileShareRepository.findByFileIdAndSharedWithId(file.getId(), targetUser.getId());
        FileShare share;
        if (existingShareOpt.isPresent()) {
            share = existingShareOpt.get();
            share.setPermissionType(permissionType);
        } else {
            User owner = userRepository.findById(ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

            share = FileShare.builder()
                    .file(file)
                    .sharedBy(owner)
                    .sharedWith(targetUser)
                    .permissionType(permissionType)
                    .build();
        }

        FileShare savedShare = fileShareRepository.save(share);

        // 5. Evict permissions cache key in Redis
        evictPermissionsCache(file.getId());

        // 6. Log audit log (Fail-secure stance: audit failures abort the operation)
        try {
            auditLogService.log(ownerId, "SHARE_CREATED", file.getId(), ipAddress,
                    "Shared file with user " + targetUser.getUsername() + " (" + permissionType + ")");
        } catch (Exception e) {
            log.error("Audit log failed for internal sharing. Failing transaction.", e);
            throw new RuntimeException("Audit logging failed, transaction rolled back for security compliance", e);
        }

        return InternalShareResponse.builder()
                .shareId(savedShare.getId())
                .fileId(file.getId())
                .sharedWith(targetUser.getEmail())
                .permission(savedShare.getPermissionType().name())
                .build();
    }

    @Transactional
    public PublicLinkResponse createPublicLink(CreatePublicLinkRequest request, UUID ownerId, String ipAddress) {
        log.info("Processing public link creation: file={}, owner={}", request.getFileId(), ownerId);

        // 1. Fetch and validate file
        FileMetadata file = fileRepository.findByIdAndOwnerIdAndDeletedFalse(request.getFileId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        // 2. Generate secure unique share code with collision checks
        String shareCode = generateUniqueShareCode();

        // 3. Setup password hashing if provided
        String passwordHash = null;
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            passwordHash = passwordEncoder.encode(request.getPassword());
        }

        Instant expiresAt = Instant.now().plusSeconds(request.getExpiresInSeconds());

        ShareLink shareLink = ShareLink.builder()
                .file(file)
                .shareCode(shareCode)
                .expiresAt(expiresAt)
                .passwordHash(passwordHash)
                .downloadLimit(request.getDownloadLimit())
                .downloadCount(0)
                .build();

        ShareLink savedLink = shareLinkRepository.save(shareLink);

        // 4. Log audit log (Fail-secure stance: audit failures abort the operation)
        try {
            auditLogService.log(ownerId, "SHARE_CREATED", file.getId(), ipAddress,
                    "Created public sharing link with code " + shareCode);
        } catch (Exception e) {
            log.error("Audit log failed for public link creation. Failing transaction.", e);
            throw new RuntimeException("Audit logging failed, transaction rolled back for security compliance", e);
        }

        return PublicLinkResponse.builder()
                .shareCode(savedLink.getShareCode())
                .shareUrl(shareBaseUrl + savedLink.getShareCode())
                .expiresAt(savedLink.getExpiresAt())
                .passwordProtected(savedLink.getPasswordHash() != null)
                .build();
    }

    @Transactional
    public FileService.DecryptedFileStream downloadPublicLink(String shareCode, String password, String ipAddress) {
        log.info("Processing public download request: shareCode={}", shareCode);

        // 1. Retrieve ShareLink and validate non-deleted file
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new ResourceNotFoundException("Link code does not exist"));

        FileMetadata file = shareLink.getFile();
        if (file.isDeleted()) {
            throw new ResourceNotFoundException("Link code does not exist");
        }

        // 2. Check expiration
        if (shareLink.getExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Link has expired");
        }

        // 3. Check download limits
        if (shareLink.getDownloadLimit() != null && shareLink.getDownloadCount() >= shareLink.getDownloadLimit()) {
            throw new AccessDeniedException("Download limit reached");
        }

        // 4. Validate password if link is protected
        if (shareLink.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
                throw new InvalidSharePasswordException("Password required but missing/invalid");
            }
        }

        // 5. Increment download count
        shareLink.setDownloadCount(shareLink.getDownloadCount() + 1);
        shareLinkRepository.save(shareLink);

        // 6. Decrypt and stream file
        Path decryptedTempFile = null;
        try {
            SecretKey fek = encryptionService.unwrapFek(file.getEncryptedFek(), file.getKekVersion());
            byte[] iv = Base64.getDecoder().decode(file.getIvGcm());

            decryptedTempFile = Files.createTempFile("pub-download-", ".tmp");
            try (InputStream encryptedStream = storageService.retrieve(file.getStoragePath());
                 OutputStream decryptedOut = Files.newOutputStream(decryptedTempFile)) {
                encryptionService.decryptStreamFully(encryptedStream, decryptedOut, fek, iv);
            }

            InputStream decryptedStream = new DeleteOnCloseInputStream(
                    Files.newInputStream(decryptedTempFile),
                    decryptedTempFile
            );

            // Log download audit event (Fail-secure: abort download if audit log fails)
            try {
                auditLogService.log(null, "GUEST_DOWNLOAD", file.getId(), ipAddress,
                        "Successfully downloaded file via public link code: " + shareCode);
            } catch (Exception auditEx) {
                log.error("Audit log failed for public link download. Failing operation for compliance.", auditEx);
                throw new RuntimeException("A server-side compliance issue occurred: audit logging failed.", auditEx);
            }

            return new FileService.DecryptedFileStream(
                    decryptedStream,
                    file.getOriginalFilename(),
                    file.getMimeType(),
                    file.getFileSizeBytes()
            );

        } catch (Exception e) {
            if (decryptedTempFile != null) {
                try {
                    Files.deleteIfExists(decryptedTempFile);
                } catch (IOException ioException) {
                    log.warn("Failed to delete temporary public download file: {}", decryptedTempFile, ioException);
                }
            }
            log.error("Failed to decrypt public file: {}", file.getId(), e);
            throw new RuntimeException("Error occurred while processing file download", e);
        }
    }

    private String generateUniqueShareCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(BASE62.charAt(RANDOM.nextInt(62)));
            }
            String code = sb.toString();
            if (!shareLinkRepository.existsByShareCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique share code after 10 attempts due to collision");
    }

    private void evictPermissionsCache(UUID fileId) {
        try {
            String cacheKey = "cache:permissions:" + fileId;
            cacheRedisTemplate.delete(cacheKey);
            log.debug("Evicted permissions cache for file: {}", fileId);
        } catch (Exception e) {
            log.error("Failed to evict permissions cache for file: {}", fileId, e);
        }
    }
}
