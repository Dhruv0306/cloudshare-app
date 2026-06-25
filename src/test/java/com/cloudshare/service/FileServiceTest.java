package com.cloudshare.service;

import com.cloudshare.dto.FileResponse;
import com.cloudshare.exception.ResourceNotFoundException;
import com.cloudshare.exception.UnsupportedMediaTypeException;
import com.cloudshare.exception.VirusDetectedException;
import com.cloudshare.model.FileMetadata;
import com.cloudshare.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import com.cloudshare.repository.FileShareRepository;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ClamAvService clamAvService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FileShareRepository fileShareRepository;

    @Mock
    private StringRedisTemplate cacheRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(
            fileRepository, 
            storageService, 
            clamAvService, 
            encryptionService, 
            auditLogService,
            fileShareRepository,
            cacheRedisTemplate
        );
    }

    @Test
    void uploadFile_success() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "clean_report.pdf",
                "application/pdf",
                "Plaintext report content".getBytes(StandardCharsets.UTF_8)
        );

        // Stub antivirus scanner as clean
        when(clamAvService.scan(any(InputStream.class))).thenReturn(true);

        // Stub encryption services
        SecretKey mockFek = new SecretKeySpec(new byte[32], "AES");
        byte[] mockIv = new byte[12];
        when(encryptionService.generateFek()).thenReturn(mockFek);
        when(encryptionService.generateIv()).thenReturn(mockIv);
        when(encryptionService.wrapFek(mockFek)).thenReturn("wrapped_fek_base64");

        // Stub repository save
        FileMetadata savedMetadata = FileMetadata.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .originalFilename("clean_report.pdf")
                .fileSizeBytes((long) file.getBytes().length)
                .mimeType("application/pdf")
                .checksumSha256("plaintext_checksum")
                .build();
        when(fileRepository.save(any(FileMetadata.class))).thenReturn(savedMetadata);

        FileResponse response = fileService.uploadFile(file, ownerId, ipAddress);

        assertNotNull(response);
        assertEquals("clean_report.pdf", response.getName());
        assertEquals("application/pdf", response.getMimeType());

        // Verify storage write and audit logging occurred
        verify(storageService).store(any(String.class), any(InputStream.class));
        verify(auditLogService).log(eq(ownerId), eq("FILE_UPLOAD"), eq(savedMetadata.getId()), eq(ipAddress), any(String.class));
    }

    @Test
    void uploadFile_virusDetected_throwsException() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "eicar_virus.txt",
                "text/plain",
                "dummy infected file content".getBytes(StandardCharsets.UTF_8)
        );

        // Stub antivirus scanner as infected
        when(clamAvService.scan(any(InputStream.class))).thenReturn(false);

        assertThrows(VirusDetectedException.class, () -> {
            fileService.uploadFile(file, ownerId, ipAddress);
        });

        // Verify audit logging reflects failure and storage write is skipped
        verify(auditLogService).log(eq(ownerId), eq("FILE_UPLOAD_FAILED"), any(), eq(ipAddress), any(String.class));
        verify(storageService, never()).store(any(String.class), any(InputStream.class));
    }

    @Test
    void uploadFile_dangerousExtension_throwsException() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious_script.sh",
                "application/x-sh",
                "rm -rf /".getBytes(StandardCharsets.UTF_8)
        );

        // Stub antivirus scanner as clean
        when(clamAvService.scan(any(InputStream.class))).thenReturn(true);

        assertThrows(UnsupportedMediaTypeException.class, () -> {
            fileService.uploadFile(file, ownerId, ipAddress);
        });

        // Verify audit logging reflects type block and storage write is skipped
        verify(auditLogService).log(eq(ownerId), eq("FILE_UPLOAD_FAILED"), any(), eq(ipAddress), any(String.class));
        verify(storageService, never()).store(any(String.class), any(InputStream.class));
    }

    @Test
    void downloadFile_success() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .ownerId(userId)
                .storagePath("storage_uuid")
                .originalFilename("sensitive_report.pdf")
                .fileSizeBytes(1234L)
                .mimeType("application/pdf")
                .encryptedFek("wrapped_fek")
                .ivGcm(Base64.getEncoder().encodeToString(new byte[12]))
                .deleted(false)
                .build();

        // Stub cache-aside lookup as OWNER
        when(cacheRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("cache:permissions:" + fileId, userId.toString())).thenReturn("OWNER");

        // Stub repository to return file
        when(fileRepository.findAccessibleFile(fileId, userId)).thenReturn(Optional.of(metadata));

        // Stub decryption keys
        SecretKey mockFek = new SecretKeySpec(new byte[32], "AES");
        when(encryptionService.unwrapFek("wrapped_fek")).thenReturn(mockFek);
        
        ByteArrayInputStream mockEncryptedStream = new ByteArrayInputStream("Encrypted Data".getBytes(StandardCharsets.UTF_8));
        when(storageService.retrieve("storage_uuid")).thenReturn(mockEncryptedStream);

        FileService.DecryptedFileStream result = fileService.downloadFile(fileId, userId, ipAddress);

        assertNotNull(result);
        assertEquals("sensitive_report.pdf", result.getFilename());
        assertEquals("application/pdf", result.getMimeType());
        assertEquals(1234L, result.getSize());

        // Verify decryption and audit logging
        verify(encryptionService).decryptStreamFully(eq(mockEncryptedStream), any(OutputStream.class), eq(mockFek), any(byte[].class));
        verify(auditLogService).log(eq(userId), eq("FILE_DOWNLOAD"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void downloadFile_notFoundOrBOLA_throwsException() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        // Stub cache-aside miss
        when(cacheRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("cache:permissions:" + fileId, userId.toString())).thenReturn(null);
        when(cacheRedisTemplate.hasKey("cache:permissions:" + fileId)).thenReturn(false);

        // Stub database lookup fail
        when(fileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            fileService.downloadFile(fileId, userId, ipAddress);
        });

        // Ensure key processing & storage retrieval never run
        verifyNoInteractions(encryptionService);
        verifyNoInteractions(storageService);
    }

    @Test
    void deleteFile_success() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .ownerId(userId)
                .originalFilename("temp.txt")
                .deleted(false)
                .build();

        when(fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, userId)).thenReturn(Optional.of(metadata));

        fileService.deleteFile(fileId, userId, ipAddress);

        // Verify soft-delete update is persisted
        assertTrue(metadata.isDeleted());
        verify(fileRepository).save(metadata);

        // Verify cache eviction
        verify(cacheRedisTemplate).delete("cache:permissions:" + fileId);

        // Verify audit logging
        verify(auditLogService).log(eq(userId), eq("FILE_DELETE"), eq(fileId), eq(ipAddress), any(String.class));
    }

    @Test
    void downloadFile_cacheHitInsufficientPermission_throwsException() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        // Stub cache-aside lookup as NONE (insufficient)
        when(cacheRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("cache:permissions:" + fileId, userId.toString())).thenReturn("NONE");

        assertThrows(ResourceNotFoundException.class, () -> {
            fileService.downloadFile(fileId, userId, ipAddress);
        });

        // Ensure key processing & storage retrieval never run
        verifyNoInteractions(encryptionService);
        verifyNoInteractions(storageService);
    }

    @Test
    void downloadFile_cacheKeyExistsButUserMissing_throwsException() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String ipAddress = "192.168.1.10";

        // Stub cache key exists but user entry is null
        when(cacheRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("cache:permissions:" + fileId, userId.toString())).thenReturn(null);
        when(cacheRedisTemplate.hasKey("cache:permissions:" + fileId)).thenReturn(true);

        assertThrows(ResourceNotFoundException.class, () -> {
            fileService.downloadFile(fileId, userId, ipAddress);
        });

        // Ensure key processing & storage retrieval never run
        verifyNoInteractions(encryptionService);
        verifyNoInteractions(storageService);
    }
}
