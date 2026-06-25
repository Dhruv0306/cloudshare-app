package com.cloudshare.service;

import com.cloudshare.dto.FileResponse;
import com.cloudshare.exception.ResourceNotFoundException;
import com.cloudshare.exception.UnsupportedMediaTypeException;
import com.cloudshare.exception.VirusDetectedException;
import com.cloudshare.model.FileMetadata;
import com.cloudshare.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final ClamAvService clamAvService;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    private final Tika tika = new Tika();

    @Transactional
    public FileResponse uploadFile(MultipartFile file, UUID ownerId, String ipAddress) {
        log.info("Processing file upload: user={}, filename={}, size={}", ownerId, file.getOriginalFilename(), file.getSize());

        Path tempFile = null;
        Path encryptedTempFile = null;
        try {
            // 1. Buffer upload to temporary file on disk (low memory JVM usage)
            tempFile = Files.createTempFile("upload-", ".tmp");
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            // 2. Scan temp file with ClamAV
            try (InputStream scanStream = Files.newInputStream(tempFile)) {
                if (!clamAvService.scan(scanStream)) {
                    auditLogService.log(ownerId, "FILE_UPLOAD_FAILED", null, ipAddress, 
                            "Malware detected in upload: " + file.getOriginalFilename());
                    throw new VirusDetectedException("Malware detected in uploaded file!");
                }
            }

            // 3. MIME magic bytes check
            String detectedMimeType;
            try (InputStream mimeStream = Files.newInputStream(tempFile)) {
                detectedMimeType = tika.detect(mimeStream, file.getOriginalFilename());
            }

            if (isDangerousMimeType(detectedMimeType) || isDangerousExtension(file.getOriginalFilename())) {
                auditLogService.log(ownerId, "FILE_UPLOAD_FAILED", null, ipAddress, 
                        "Blocked upload of disallowed file type: " + file.getOriginalFilename());
                throw new UnsupportedMediaTypeException("This file type or media extension is not allowed");
            }

            // 4. Encrypt plaintext stream on-the-fly and calculate checksum
            SecretKey fek = encryptionService.generateFek();
            byte[] iv = encryptionService.generateIv();
            String storagePath = UUID.randomUUID().toString();

            MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
            encryptedTempFile = Files.createTempFile("enc-", ".tmp");

            try (InputStream plaintextIn = Files.newInputStream(tempFile);
                 DigestInputStream digestIn = new DigestInputStream(plaintextIn, sha256Digest);
                 OutputStream encryptedOut = Files.newOutputStream(encryptedTempFile)) {
                
                encryptionService.encryptStream(digestIn, encryptedOut, fek, iv);
            }

            // Calculate plaintext hash
            byte[] checksumBytes = sha256Digest.digest();
            String checksumSha256 = bytesToHex(checksumBytes);

            // 5. Stream encrypted temp file to the pluggable storage backend
            try (InputStream encryptedIn = Files.newInputStream(encryptedTempFile)) {
                storageService.store(storagePath, encryptedIn);
            }

            // 6. Wrap FEK with KEK
            String encryptedFek = encryptionService.wrapFek(fek);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            // 7. Persist metadata
            FileMetadata metadata = FileMetadata.builder()
                    .ownerId(ownerId)
                    .storagePath(storagePath)
                    .originalFilename(file.getOriginalFilename())
                    .fileSizeBytes(file.getSize())
                    .mimeType(detectedMimeType)
                    .checksumSha256(checksumSha256)
                    .encryptedFek(encryptedFek)
                    .ivGcm(ivBase64)
                    .kekVersion(1)
                    .deleted(false)
                    .build();

            FileMetadata savedMetadata = fileRepository.save(metadata);

            // 8. Log success audit event
            auditLogService.log(ownerId, "FILE_UPLOAD", savedMetadata.getId(), ipAddress, 
                    "Successfully uploaded file: " + file.getOriginalFilename());

            return mapToFileResponse(savedMetadata);

        } catch (VirusDetectedException | UnsupportedMediaTypeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Internal error during file upload pipeline execution", e);
            throw new RuntimeException("An error occurred during file upload", e);
        } finally {
            // Guaranteed cleanup of temporary files to prevent disk leak
            cleanupTempFile(tempFile);
            cleanupTempFile(encryptedTempFile);
        }
    }

    @Transactional(readOnly = true)
    public DecryptedFileStream downloadFile(UUID fileId, UUID userId, String ipAddress) {
        log.info("Processing file download request: user={}, fileId={}", userId, fileId);

        // Fetch file details with BOLA validation
        FileMetadata metadata = fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        try {
            // Unwrap FEK with KEK using AESWrap
            SecretKey fek = encryptionService.unwrapFek(metadata.getEncryptedFek());
            byte[] iv = Base64.getDecoder().decode(metadata.getIvGcm());

            // Retrieve and wrap streaming decryption
            InputStream encryptedStream = storageService.retrieve(metadata.getStoragePath());
            InputStream decryptedStream = encryptionService.decryptStream(encryptedStream, fek, iv);

            // Log download audit event
            auditLogService.log(userId, "FILE_DOWNLOAD", fileId, ipAddress, 
                    "Successfully downloaded file: " + metadata.getOriginalFilename());

            return new DecryptedFileStream(
                    decryptedStream,
                    metadata.getOriginalFilename(),
                    metadata.getMimeType(),
                    metadata.getFileSizeBytes()
            );

        } catch (Exception e) {
            log.error("Failed to retrieve or decrypt file for download: {}", fileId, e);
            throw new RuntimeException("Error occurred while processing file download", e);
        }
    }

    @Transactional
    public void deleteFile(UUID fileId, UUID userId, String ipAddress) {
        log.info("Processing file deletion request: user={}, fileId={}", userId, fileId);

        // Fetch file details with BOLA validation
        FileMetadata metadata = fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        metadata.setDeleted(true);
        fileRepository.save(metadata);

        // Log delete audit event
        auditLogService.log(userId, "FILE_DELETE", fileId, ipAddress, 
                "Successfully soft-deleted file: " + metadata.getOriginalFilename());
    }

    @Transactional(readOnly = true)
    public Page<FileResponse> listFiles(UUID userId, Pageable pageable) {
        return fileRepository.findByOwnerIdAndDeletedFalse(userId, pageable)
                .map(this::mapToFileResponse);
    }

    private FileResponse mapToFileResponse(FileMetadata metadata) {
        return FileResponse.builder()
                .id(metadata.getId())
                .name(metadata.getOriginalFilename())
                .sizeBytes(metadata.getFileSizeBytes())
                .mimeType(metadata.getMimeType())
                .checksum(metadata.getChecksumSha256())
                .uploadedAt(metadata.getCreatedAt())
                .build();
    }

    private boolean isDangerousExtension(String filename) {
        if (filename == null) return true;
        String lower = filename.toLowerCase();
        return lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".bat") ||
               lower.endsWith(".cmd") || lower.endsWith(".sh") || lower.endsWith(".bash") ||
               lower.endsWith(".scr") || lower.endsWith(".pif") || lower.endsWith(".vbs") ||
               lower.endsWith(".js") || lower.endsWith(".jar") || lower.endsWith(".msi") ||
               lower.endsWith(".jsp") || lower.endsWith(".asp") || lower.endsWith(".aspx");
    }

    private boolean isDangerousMimeType(String mimeType) {
        if (mimeType == null) return true;
        String lower = mimeType.toLowerCase();
        return lower.equals("application/x-msdownload") || 
               lower.equals("application/x-sh") || 
               lower.equals("application/x-bash") || 
               lower.equals("application/x-msdos-program") ||
               lower.equals("application/javascript") ||
               lower.equals("text/html");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void cleanupTempFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete temporary upload file: {}", path, e);
            }
        }
    }

    @Value
    public static class DecryptedFileStream {
        InputStream inputStream;
        String filename;
        String mimeType;
        long size;
    }
}
