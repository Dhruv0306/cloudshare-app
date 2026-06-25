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
        when(encryptionService.unwrapFek("fek_wrapped")).thenReturn(mockFek);
        when(storageService.retrieve("store/123")).thenReturn(new ByteArrayInputStream("encrypted_data".getBytes()));

        FileService.DecryptedFileStream stream = shareService.downloadPublicLink(shareCode, "my_pass", ipAddress);

        assertNotNull(stream);
        assertEquals("pic.png", stream.getFilename());
        assertEquals("image/png", stream.getMimeType());
        assertEquals(2, shareLink.getDownloadCount());

        verify(shareLinkRepository).save(shareLink);
        verify(auditLogService).log(isNull(), eq("FILE_DOWNLOAD"), eq(fileId), eq(ipAddress), any(String.class));
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
}
