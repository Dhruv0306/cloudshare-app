package com.cloudshare.service;

import com.cloudshare.dto.*;
import com.cloudshare.exception.AccessDeniedException;
import com.cloudshare.exception.InvalidSharePasswordException;
import com.cloudshare.model.*;
import com.cloudshare.repository.FileRepository;
import com.cloudshare.repository.FileShareRepository;
import com.cloudshare.repository.ShareLinkRepository;
import com.cloudshare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock private FileShareRepository fileShareRepository;
    @Mock private ShareLinkRepository shareLinkRepository;
    @Mock private FileRepository fileRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private EncryptionService encryptionService;
    @Mock private StorageService storageService;
    @Mock private StringRedisTemplate cacheRedisTemplate;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(
                fileShareRepository, shareLinkRepository, fileRepository, userRepository,
                passwordEncoder, auditLogService, encryptionService, storageService, cacheRedisTemplate
        );
    }

    @Test
    void shareFileInternally_success() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        InternalShareRequest request = InternalShareRequest.builder()
                .fileId(fileId)
                .targetUsernameOrEmail("janedoe")
                .permissionType("READ")
                .build();

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("report.pdf").deleted(false).build();
        User targetUser = User.builder().id(targetId).username("janedoe").email("janedoe@example.com").build();
        User ownerUser = User.builder().id(ownerId).username("john").email("john@example.com").build();

        when(fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, ownerId)).thenReturn(Optional.of(file));
        when(userRepository.findByUsernameOrEmail("janedoe", "janedoe")).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(fileShareRepository.findByFileIdAndSharedWithId(fileId, targetId)).thenReturn(Optional.empty());

        FileShare savedShare = FileShare.builder()
                .id(UUID.randomUUID())
                .file(file)
                .sharedBy(ownerUser)
                .sharedWith(targetUser)
                .permissionType(PermissionType.READ)
                .build();
        when(fileShareRepository.save(any(FileShare.class))).thenReturn(savedShare);

        InternalShareResponse response = shareService.shareFileInternally(request, ownerId, ipAddress);

        assertNotNull(response);
        assertEquals(fileId, response.getFileId());
        assertEquals("janedoe@example.com", response.getSharedWith());
        assertEquals("READ", response.getPermission());

        verify(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        verify(auditLogService).log(eq(ownerId), eq("SHARE_CREATED"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void shareFileInternally_shareWithSelf_throwsException() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        InternalShareRequest request = InternalShareRequest.builder()
                .fileId(fileId)
                .targetUsernameOrEmail("john")
                .permissionType("READ")
                .build();

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).deleted(false).build();
        User selfUser = User.builder().id(ownerId).username("john").build();

        when(fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, ownerId)).thenReturn(Optional.of(file));
        when(userRepository.findByUsernameOrEmail("john", "john")).thenReturn(Optional.of(selfUser));

        assertThrows(IllegalArgumentException.class, () -> shareService.shareFileInternally(request, ownerId, "127.0.0.1"));
    }

    @Test
    void createPublicLink_success() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        CreatePublicLinkRequest request = CreatePublicLinkRequest.builder()
                .fileId(fileId)
                .expiresInSeconds(3600L)
                .password("LinkSecret")
                .downloadLimit(3)
                .build();

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("doc.pdf").deleted(false).build();

        when(fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, ownerId)).thenReturn(Optional.of(file));
        when(passwordEncoder.encode("LinkSecret")).thenReturn("hashed_password");
        when(shareLinkRepository.existsByShareCode(any(String.class))).thenReturn(false);

        ShareLink savedLink = ShareLink.builder()
                .shareCode("ABCDEFGH")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .passwordHash("hashed_password")
                .build();
        when(shareLinkRepository.save(any(ShareLink.class))).thenReturn(savedLink);

        PublicLinkResponse response = shareService.createPublicLink(request, ownerId, ipAddress);

        assertNotNull(response);
        assertEquals("ABCDEFGH", response.getShareCode());
        assertTrue(response.isPasswordProtected());
        verify(auditLogService).log(eq(ownerId), eq("SHARE_CREATED"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void downloadPublicLink_success() throws Exception {
        UUID fileId = UUID.randomUUID();
        String shareCode = "ABCDEFGH";
        String ipAddress = "192.168.1.10";

        FileMetadata file = FileMetadata.builder()
                .id(fileId)
                .storagePath("store/123")
                .originalFilename("pic.png")
                .mimeType("image/png")
                .fileSizeBytes(100L)
                .encryptedFek("fek_wrapped")
                .ivGcm(Base64.getEncoder().encodeToString(new byte[12]))
                .deleted(false)
                .build();

        ShareLink shareLink = ShareLink.builder()
                .file(file)
                .shareCode(shareCode)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .downloadLimit(5)
                .downloadCount(1)
                .passwordHash("hashed_pass")
                .build();

        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.of(shareLink));
        when(passwordEncoder.matches("my_pass", "hashed_pass")).thenReturn(true);

        SecretKey mockFek = new SecretKeySpec(new byte[32], "AES");
        when(encryptionService.unwrapFek("fek_wrapped", 1)).thenReturn(mockFek);
        when(storageService.retrieve("store/123")).thenReturn(new ByteArrayInputStream("encrypted_data".getBytes()));

        FileService.DecryptedFileStream stream = shareService.downloadPublicLink(shareCode, "my_pass", ipAddress);

        assertNotNull(stream);
        assertEquals("pic.png", stream.getFilename());
        assertEquals("image/png", stream.getMimeType());
        assertEquals(2, shareLink.getDownloadCount());

        verify(shareLinkRepository).save(shareLink);
        verify(auditLogService).log(isNull(), eq("GUEST_DOWNLOAD"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void downloadPublicLink_expired_throwsException() {
        String shareCode = "ABCDEFGH";
        FileMetadata file = FileMetadata.builder().deleted(false).build();
        ShareLink shareLink = ShareLink.builder()
                .file(file)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.of(shareLink));

        assertThrows(AccessDeniedException.class, () -> shareService.downloadPublicLink(shareCode, null, "127.0.0.1"));
    }

    @Test
    void downloadPublicLink_limitReached_throwsException() {
        String shareCode = "ABCDEFGH";
        FileMetadata file = FileMetadata.builder().deleted(false).build();
        ShareLink shareLink = ShareLink.builder()
                .file(file)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .downloadLimit(2)
                .downloadCount(2)
                .build();
        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.of(shareLink));

        assertThrows(AccessDeniedException.class, () -> shareService.downloadPublicLink(shareCode, null, "127.0.0.1"));
    }

    @Test
    void downloadPublicLink_passwordRequired_throwsException() {
        String shareCode = "ABCDEFGH";
        FileMetadata file = FileMetadata.builder().deleted(false).build();
        ShareLink shareLink = ShareLink.builder()
                .file(file)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .passwordHash("hashed_pass")
                .build();
        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.of(shareLink));
        when(passwordEncoder.matches(any(String.class), eq("hashed_pass"))).thenReturn(false);

        assertThrows(InvalidSharePasswordException.class, () -> shareService.downloadPublicLink(shareCode, "wrong_pass", "127.0.0.1"));
    }

    @Test
    void revokeInternalShare_successByOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("report.pdf").deleted(false).build();
        User targetUser = User.builder().id(targetId).username("janedoe").email("janedoe@example.com").build();
        User ownerUser = User.builder().id(ownerId).username("john").email("john@example.com").build();

        FileShare fileShare = FileShare.builder()
                .id(shareId)
                .file(file)
                .sharedBy(ownerUser)
                .sharedWith(targetUser)
                .permissionType(PermissionType.READ)
                .build();

        when(fileShareRepository.findById(shareId)).thenReturn(Optional.of(fileShare));

        shareService.revokeInternalShare(shareId, ownerId, ipAddress);

        verify(fileShareRepository).delete(fileShare);
        verify(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        verify(auditLogService).log(eq(ownerId), eq("SHARE_REVOKED"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void revokeInternalShare_successBySharedBy() {
        UUID ownerId = UUID.randomUUID();
        UUID sharedById = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("report.pdf").deleted(false).build();
        User targetUser = User.builder().id(targetId).username("janedoe").email("janedoe@example.com").build();
        User sharingUser = User.builder().id(sharedById).username("john").email("john@example.com").build();

        FileShare fileShare = FileShare.builder()
                .id(shareId)
                .file(file)
                .sharedBy(sharingUser)
                .sharedWith(targetUser)
                .permissionType(PermissionType.READ)
                .build();

        when(fileShareRepository.findById(shareId)).thenReturn(Optional.of(fileShare));

        shareService.revokeInternalShare(shareId, sharedById, ipAddress);

        verify(fileShareRepository).delete(fileShare);
        verify(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        verify(auditLogService).log(eq(sharedById), eq("SHARE_REVOKED"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void revokeInternalShare_nonOwner_throwsException() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        UUID randomUserId = UUID.randomUUID();

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("report.pdf").deleted(false).build();
        User targetUser = User.builder().id(targetId).username("janedoe").build();
        User ownerUser = User.builder().id(ownerId).username("john").build();

        FileShare fileShare = FileShare.builder()
                .id(shareId)
                .file(file)
                .sharedBy(ownerUser)
                .sharedWith(targetUser)
                .build();

        when(fileShareRepository.findById(shareId)).thenReturn(Optional.of(fileShare));

        assertThrows(com.cloudshare.exception.ResourceNotFoundException.class, 
                () -> shareService.revokeInternalShare(shareId, randomUserId, "127.0.0.1"));

        verify(fileShareRepository, never()).delete(any());
        verify(cacheRedisTemplate, never()).delete(anyString());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void revokeInternalShare_notExist_throwsException() {
        UUID shareId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(fileShareRepository.findById(shareId)).thenReturn(Optional.empty());

        assertThrows(com.cloudshare.exception.ResourceNotFoundException.class, 
                () -> shareService.revokeInternalShare(shareId, callerId, "127.0.0.1"));
    }

    @Test
    void revokePublicLink_success() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String shareCode = "XYZ12345";
        String ipAddress = "192.168.1.10";

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("doc.pdf").deleted(false).build();
        ShareLink shareLink = ShareLink.builder().file(file).shareCode(shareCode).build();

        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.of(shareLink));

        shareService.revokePublicLink(shareCode, ownerId, ipAddress);

        verify(shareLinkRepository).delete(shareLink);
        verify(auditLogService).log(eq(ownerId), eq("SHARE_LINK_REVOKED"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void revokePublicLink_nonOwner_throwsException() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID randomUserId = UUID.randomUUID();
        String shareCode = "XYZ12345";

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("doc.pdf").deleted(false).build();
        ShareLink shareLink = ShareLink.builder().file(file).shareCode(shareCode).build();

        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.of(shareLink));

        assertThrows(com.cloudshare.exception.ResourceNotFoundException.class, 
                () -> shareService.revokePublicLink(shareCode, randomUserId, "127.0.0.1"));

        verify(shareLinkRepository, never()).delete(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void revokePublicLink_notExist_throwsException() {
        String shareCode = "XYZ12345";
        UUID callerId = UUID.randomUUID();
        when(shareLinkRepository.findByShareCode(shareCode)).thenReturn(Optional.empty());

        assertThrows(com.cloudshare.exception.ResourceNotFoundException.class, 
                () -> shareService.revokePublicLink(shareCode, callerId, "127.0.0.1"));
    }

    @Test
    void shareFileInternally_evictionDeleteThrows_setsBypassMarker() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        InternalShareRequest request = InternalShareRequest.builder()
                .fileId(fileId)
                .targetUsernameOrEmail("janedoe")
                .permissionType("READ")
                .build();

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("report.pdf").deleted(false).build();
        User targetUser = User.builder().id(targetId).username("janedoe").email("janedoe@example.com").build();
        User ownerUser = User.builder().id(ownerId).username("john").email("john@example.com").build();

        when(fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, ownerId)).thenReturn(Optional.of(file));
        when(userRepository.findByUsernameOrEmail("janedoe", "janedoe")).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(fileShareRepository.findByFileIdAndSharedWithId(fileId, targetId)).thenReturn(Optional.empty());

        FileShare savedShare = FileShare.builder()
                .id(UUID.randomUUID())
                .file(file)
                .sharedBy(ownerUser)
                .sharedWith(targetUser)
                .permissionType(PermissionType.READ)
                .build();
        when(fileShareRepository.save(any(FileShare.class))).thenReturn(savedShare);

        // Make cacheRedisTemplate.delete throw exception
        doThrow(new RuntimeException("Redis error")).when(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        when(cacheRedisTemplate.opsForValue()).thenReturn(valueOperations);

        InternalShareResponse response = shareService.shareFileInternally(request, ownerId, ipAddress);

        assertNotNull(response);
        verify(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        verify(valueOperations).set(eq("cache:permissions:bypass:" + fileId), eq("true"), eq(java.time.Duration.ofMinutes(10)));
        verify(auditLogService).log(eq(ownerId), eq("SHARE_CREATED"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void shareFileInternally_evictionDeleteAndBypassSetThrow_doesNotCrash() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        InternalShareRequest request = InternalShareRequest.builder()
                .fileId(fileId)
                .targetUsernameOrEmail("janedoe")
                .permissionType("READ")
                .build();

        FileMetadata file = FileMetadata.builder().id(fileId).ownerId(ownerId).originalFilename("report.pdf").deleted(false).build();
        User targetUser = User.builder().id(targetId).username("janedoe").email("janedoe@example.com").build();
        User ownerUser = User.builder().id(ownerId).username("john").email("john@example.com").build();

        when(fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, ownerId)).thenReturn(Optional.of(file));
        when(userRepository.findByUsernameOrEmail("janedoe", "janedoe")).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(fileShareRepository.findByFileIdAndSharedWithId(fileId, targetId)).thenReturn(Optional.empty());

        FileShare savedShare = FileShare.builder()
                .id(UUID.randomUUID())
                .file(file)
                .sharedBy(ownerUser)
                .sharedWith(targetUser)
                .permissionType(PermissionType.READ)
                .build();
        when(fileShareRepository.save(any(FileShare.class))).thenReturn(savedShare);

        // Make cacheRedisTemplate.delete throw exception
        doThrow(new RuntimeException("Redis error")).when(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        when(cacheRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis write error")).when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));

        InternalShareResponse response = shareService.shareFileInternally(request, ownerId, ipAddress);

        assertNotNull(response);
        verify(cacheRedisTemplate).delete("cache:permissions:" + fileId);
        verify(valueOperations).set(eq("cache:permissions:bypass:" + fileId), eq("true"), eq(java.time.Duration.ofMinutes(10)));
        verify(auditLogService).log(eq(ownerId), eq("SHARE_CREATED"), eq(fileId), eq(ipAddress), any(String.class));
    }
}
