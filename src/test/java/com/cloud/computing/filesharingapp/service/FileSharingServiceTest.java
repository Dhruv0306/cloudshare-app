package com.cloud.computing.filesharingapp.service;

import com.cloud.computing.filesharingapp.dto.ShareRequest;
import com.cloud.computing.filesharingapp.dto.ShareResponse;
import com.cloud.computing.filesharingapp.entity.FileEntity;
import com.cloud.computing.filesharingapp.entity.FileShare;
import com.cloud.computing.filesharingapp.entity.ShareAccessType;
import com.cloud.computing.filesharingapp.entity.SharePermission;
import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.FileRepository;
import com.cloud.computing.filesharingapp.repository.FileShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive test suite for FileSharingService.
 * 
 * <p>This test class covers all major functionality of the FileSharingService including:
 * <ul>
 *   <li>Share creation with various configurations</li>
 *   <li>Token generation and validation</li>
 *   <li>Access tracking and limits enforcement</li>
 *   <li>Share management operations (update, revoke)</li>
 *   <li>Cleanup and maintenance operations</li>
 *   <li>Error handling and edge cases</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileSharingService Tests")
@SuppressWarnings("deprecation") // Allow use of deprecated methods in tests
class FileSharingServiceTest {

    @Mock
    private FileShareRepository fileShareRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private ShareAccessService shareAccessService;

    @Mock
    private AdvancedSecurityService advancedSecurityService;

    @Mock
    private RateLimitingService rateLimitingService;

    @InjectMocks
    private FileSharingService fileSharingService;

    private User testUser;
    private FileEntity testFile;
    private ShareRequest shareRequest;
    private FileShare testShare;

    @BeforeEach
    void setUp() {
        // Set up test data
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setId(1L);

        testFile = new FileEntity("uuid_test.txt", "test.txt", "text/plain", 1024L, "/path/test.txt", testUser);
        testFile.setId(1L);

        shareRequest = new ShareRequest(SharePermission.DOWNLOAD);
        shareRequest.setExpiresAt(LocalDateTime.now().plusDays(7));
        shareRequest.setMaxAccess(10);

        testShare = new FileShare(testFile, testUser, "test-token-123", SharePermission.DOWNLOAD);
        testShare.setId(1L);
        testShare.setExpiresAt(shareRequest.getExpiresAt());
        testShare.setMaxAccess(shareRequest.getMaxAccess());

        // Set base URL for URL generation
        ReflectionTestUtils.setField(fileSharingService, "baseUrl", "http://localhost:8080");
        
        // Mock ShareAccessService to allow access by default (lenient to avoid unnecessary stubbing errors)
        lenient().when(shareAccessService.validateAccess(any(FileShare.class), anyString(), any(ShareAccessType.class)))
            .thenReturn(ShareAccessService.AccessValidationResult.allowed());
        
        // Mock AdvancedSecurityService to allow share creation by default
        lenient().when(advancedSecurityService.validateShareCreation(any(User.class), anyString(), anyString()))
            .thenReturn(AdvancedSecurityService.ShareCreationValidationResult.allowed());
        
        // Mock RateLimitingService to allow share creation by default
        lenient().when(rateLimitingService.validateShareCreation(any(User.class), anyString()))
            .thenReturn(RateLimitingService.RateLimitResult.allowed());
        lenient().when(rateLimitingService.validateShareAccess(any(FileShare.class), anyString(), any(ShareAccessType.class), any()))
            .thenReturn(RateLimitingService.RateLimitResult.allowed());
    }

    @Nested
    @DisplayName("Share Creation Tests")
    class ShareCreationTests {

        @Test
        @DisplayName("Should create share successfully with valid parameters")
        void shouldCreateShareSuccessfully() {
            // Given
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.save(any(FileShare.class))).thenReturn(testShare);

            // When
            ShareResponse response = fileSharingService.createShare(1L, shareRequest, testUser);

            // Then
            assertNotNull(response);
            assertEquals(testShare.getId(), response.getShareId());
            assertEquals(testShare.getShareToken(), response.getShareToken());
            assertEquals(SharePermission.DOWNLOAD, response.getPermission());
            assertTrue(response.getShareUrl().contains(testShare.getShareToken()));
            
            verify(fileRepository).findByIdAndOwner(1L, testUser);
            verify(fileShareRepository).save(any(FileShare.class));
        }

        @Test
        @DisplayName("Should throw exception when file not found")
        void shouldThrowExceptionWhenFileNotFound() {
            // Given
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.empty());

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                fileSharingService.createShare(1L, shareRequest, testUser);
            });

            assertEquals("File not found or access denied", exception.getMessage());
            verify(fileRepository).findByIdAndOwner(1L, testUser);
            verify(fileShareRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create share with VIEW_ONLY permission")
        void shouldCreateShareWithViewOnlyPermission() {
            // Given
            shareRequest.setPermission(SharePermission.VIEW_ONLY);
            FileShare viewOnlyShare = new FileShare(testFile, testUser, "view-token", SharePermission.VIEW_ONLY);
            viewOnlyShare.setId(2L);

            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.save(any(FileShare.class))).thenReturn(viewOnlyShare);

            // When
            ShareResponse response = fileSharingService.createShare(1L, shareRequest, testUser);

            // Then
            assertEquals(SharePermission.VIEW_ONLY, response.getPermission());
            verify(fileShareRepository).save(argThat(share -> 
                share.getPermission() == SharePermission.VIEW_ONLY
            ));
        }

        @Test
        @DisplayName("Should create share without expiration")
        void shouldCreateShareWithoutExpiration() {
            // Given
            shareRequest.setExpiresAt(null);
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.save(any(FileShare.class))).thenReturn(testShare);

            // When
            ShareResponse response = fileSharingService.createShare(1L, shareRequest, testUser);

            // Then
            assertNotNull(response);
            verify(fileShareRepository).save(argThat(share -> 
                share.getExpiresAt() == null
            ));
        }
    }

    @Nested
    @DisplayName("Token Generation and Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should generate unique UUID-based token")
        void shouldGenerateUniqueToken() {
            // When
            String token1 = fileSharingService.generateShareToken();
            String token2 = fileSharingService.generateShareToken();

            // Then
            assertNotNull(token1);
            assertNotNull(token2);
            assertNotEquals(token1, token2);
            
            // Verify UUID format
            assertDoesNotThrow(() -> UUID.fromString(token1));
            assertDoesNotThrow(() -> UUID.fromString(token2));
        }

        @Test
        @DisplayName("Should validate active and non-expired share token")
        void shouldValidateActiveShareToken() {
            // Given
            testShare.setActive(true);
            testShare.setExpiresAt(LocalDateTime.now().plusDays(1));
            testShare.setAccessCount(5);
            testShare.setMaxAccess(10);

            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));

            // When
            Optional<FileShare> result = fileSharingService.validateShareToken("test-token-123");

            // Then
            assertTrue(result.isPresent());
            assertEquals(testShare, result.get());
        }

        @Test
        @DisplayName("Should reject null or empty token")
        void shouldRejectNullOrEmptyToken() {
            // When & Then
            assertTrue(fileSharingService.validateShareToken(null).isEmpty());
            assertTrue(fileSharingService.validateShareToken("").isEmpty());
            assertTrue(fileSharingService.validateShareToken("   ").isEmpty());
        }

        @Test
        @DisplayName("Should reject non-existent token")
        void shouldRejectNonExistentToken() {
            // Given
            when(fileShareRepository.findByShareToken("invalid-token")).thenReturn(Optional.empty());

            // When
            Optional<FileShare> result = fileSharingService.validateShareToken("invalid-token");

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject inactive share")
        void shouldRejectInactiveShare() {
            // Given
            testShare.setActive(false);
            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));

            // When
            Optional<FileShare> result = fileSharingService.validateShareToken("test-token-123");

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject expired share")
        void shouldRejectExpiredShare() {
            // Given
            testShare.setActive(true);
            testShare.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));

            // When
            Optional<FileShare> result = fileSharingService.validateShareToken("test-token-123");

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject share that reached max access")
        void shouldRejectShareThatReachedMaxAccess() {
            // Given
            testShare.setActive(true);
            testShare.setExpiresAt(LocalDateTime.now().plusDays(1));
            testShare.setAccessCount(10);
            testShare.setMaxAccess(10);
            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));

            // When
            Optional<FileShare> result = fileSharingService.validateShareToken("test-token-123");

            // Then
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Access Tracking Tests")
    class AccessTrackingTests {

        @Test
        @DisplayName("Should record access for valid share")
        void shouldRecordAccessForValidShare() {
            // Given
            testShare.setActive(true);
            testShare.setExpiresAt(LocalDateTime.now().plusDays(1));
            testShare.setAccessCount(5);
            testShare.setMaxAccess(10);

            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));
            when(fileShareRepository.save(testShare)).thenReturn(testShare);

            // When
            boolean result = fileSharingService.recordShareAccess("test-token-123", "192.168.1.1", "TestBrowser/1.0", ShareAccessType.VIEW);

            // Then
            assertTrue(result);
            assertEquals(6, testShare.getAccessCount());
            verify(fileShareRepository).save(testShare);
            verify(shareAccessService).logAccess(testShare, "192.168.1.1", "TestBrowser/1.0", ShareAccessType.VIEW);
        }

        @Test
        @DisplayName("Should not record access for invalid share")
        void shouldNotRecordAccessForInvalidShare() {
            // Given
            when(fileShareRepository.findByShareToken("invalid-token")).thenReturn(Optional.empty());

            // When
            boolean result = fileSharingService.recordShareAccess("invalid-token", "192.168.1.1", "TestBrowser/1.0", ShareAccessType.VIEW);

            // Then
            assertFalse(result);
            verify(fileShareRepository, never()).save(any());
            verify(shareAccessService, never()).logAccess(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Share Management Tests")
    class ShareManagementTests {

        @Test
        @DisplayName("Should get user shares successfully")
        void shouldGetUserSharesSuccessfully() {
            // Given
            FileShare share1 = new FileShare(testFile, testUser, "token1", SharePermission.DOWNLOAD);
            share1.setId(1L);
            FileShare share2 = new FileShare(testFile, testUser, "token2", SharePermission.VIEW_ONLY);
            share2.setId(2L);
            
            List<FileShare> shares = Arrays.asList(share1, share2);
            when(fileShareRepository.findByOwnerAndActiveTrueOrderByCreatedAtDesc(testUser)).thenReturn(shares);

            // When
            List<ShareResponse> result = fileSharingService.getUserShares(testUser);

            // Then
            assertEquals(2, result.size());
            assertEquals(share1.getId(), result.get(0).getShareId());
            assertEquals(share2.getId(), result.get(1).getShareId());
        }

        @Test
        @DisplayName("Should get specific user share")
        void shouldGetSpecificUserShare() {
            // Given
            when(fileShareRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testShare));

            // When
            Optional<ShareResponse> result = fileSharingService.getUserShare(1L, testUser);

            // Then
            assertTrue(result.isPresent());
            assertEquals(testShare.getId(), result.get().getShareId());
        }

        @Test
        @DisplayName("Should return empty for non-existent share")
        void shouldReturnEmptyForNonExistentShare() {
            // Given
            when(fileShareRepository.findByIdAndOwner(999L, testUser)).thenReturn(Optional.empty());

            // When
            Optional<ShareResponse> result = fileSharingService.getUserShare(999L, testUser);

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should update share permission successfully")
        void shouldUpdateSharePermissionSuccessfully() {
            // Given
            testShare.setPermission(SharePermission.DOWNLOAD);
            when(fileShareRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testShare));
            when(fileShareRepository.save(testShare)).thenReturn(testShare);

            // When
            Optional<ShareResponse> result = fileSharingService.updateSharePermission(1L, SharePermission.VIEW_ONLY, testUser);

            // Then
            assertTrue(result.isPresent());
            assertEquals(SharePermission.VIEW_ONLY, testShare.getPermission());
            verify(fileShareRepository).save(testShare);
        }

        @Test
        @DisplayName("Should return empty when updating non-existent share")
        void shouldReturnEmptyWhenUpdatingNonExistentShare() {
            // Given
            when(fileShareRepository.findByIdAndOwner(999L, testUser)).thenReturn(Optional.empty());

            // When
            Optional<ShareResponse> result = fileSharingService.updateSharePermission(999L, SharePermission.VIEW_ONLY, testUser);

            // Then
            assertTrue(result.isEmpty());
            verify(fileShareRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should revoke share successfully")
        void shouldRevokeShareSuccessfully() {
            // Given
            testShare.setActive(true);
            when(fileShareRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testShare));
            when(fileShareRepository.save(testShare)).thenReturn(testShare);

            // When
            boolean result = fileSharingService.revokeShare(1L, testUser);

            // Then
            assertTrue(result);
            assertFalse(testShare.isActive());
            verify(fileShareRepository).save(testShare);
        }

        @Test
        @DisplayName("Should return false when revoking non-existent share")
        void shouldReturnFalseWhenRevokingNonExistentShare() {
            // Given
            when(fileShareRepository.findByIdAndOwner(999L, testUser)).thenReturn(Optional.empty());

            // When
            boolean result = fileSharingService.revokeShare(999L, testUser);

            // Then
            assertFalse(result);
            verify(fileShareRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should revoke all shares for file")
        void shouldRevokeAllSharesForFile() {
            // Given
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.deactivateSharesForFile(testFile)).thenReturn(3);

            // When
            int result = fileSharingService.revokeAllSharesForFile(1L, testUser);

            // Then
            assertEquals(3, result);
            verify(fileShareRepository).deactivateSharesForFile(testFile);
        }

        @Test
        @DisplayName("Should return 0 when revoking shares for non-existent file")
        void shouldReturnZeroWhenRevokingSharesForNonExistentFile() {
            // Given
            when(fileRepository.findByIdAndOwner(999L, testUser)).thenReturn(Optional.empty());

            // When
            int result = fileSharingService.revokeAllSharesForFile(999L, testUser);

            // Then
            assertEquals(0, result);
            verify(fileShareRepository, never()).deactivateSharesForFile(any());
        }
    }

    @Nested
    @DisplayName("Cleanup Operations Tests")
    class CleanupOperationsTests {

        @Test
        @DisplayName("Should cleanup expired shares")
        void shouldCleanupExpiredShares() {
            // Given
            when(fileShareRepository.deactivateExpiredShares(any(LocalDateTime.class))).thenReturn(5);
            when(fileShareRepository.deactivateMaxAccessReachedShares()).thenReturn(3);

            // When
            int result = fileSharingService.cleanupExpiredShares();

            // Then
            assertEquals(8, result);
            verify(fileShareRepository).deactivateExpiredShares(any(LocalDateTime.class));
            verify(fileShareRepository).deactivateMaxAccessReachedShares();
        }

        @Test
        @DisplayName("Should cleanup shares with no expired or max access reached")
        void shouldCleanupSharesWithNoExpiredOrMaxAccessReached() {
            // Given
            when(fileShareRepository.deactivateExpiredShares(any(LocalDateTime.class))).thenReturn(0);
            when(fileShareRepository.deactivateMaxAccessReachedShares()).thenReturn(0);

            // When
            int result = fileSharingService.cleanupExpiredShares();

            // Then
            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("URL Building Tests")
    class UrlBuildingTests {

        @Test
        @DisplayName("Should build correct share URL")
        void shouldBuildCorrectShareUrl() {
            // Given
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.save(any(FileShare.class))).thenReturn(testShare);

            // When
            ShareResponse response = fileSharingService.createShare(1L, shareRequest, testUser);

            // Then
            String expectedUrl = "http://localhost:8080/api/shares/" + testShare.getShareToken();
            assertEquals(expectedUrl, response.getShareUrl());
        }

        @Test
        @DisplayName("Should build share URL with custom base URL")
        void shouldBuildShareUrlWithCustomBaseUrl() {
            // Given
            ReflectionTestUtils.setField(fileSharingService, "baseUrl", "https://example.com");
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.save(any(FileShare.class))).thenReturn(testShare);

            // When
            ShareResponse response = fileSharingService.createShare(1L, shareRequest, testUser);

            // Then
            String expectedUrl = "https://example.com/api/shares/" + testShare.getShareToken();
            assertEquals(expectedUrl, response.getShareUrl());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle share with unlimited access")
        void shouldHandleShareWithUnlimitedAccess() {
            // Given
            shareRequest.setMaxAccess(null);
            testShare.setMaxAccess(null);
            testShare.setAccessCount(1000); // High access count
            
            when(fileRepository.findByIdAndOwner(1L, testUser)).thenReturn(Optional.of(testFile));
            when(fileShareRepository.save(any(FileShare.class))).thenReturn(testShare);

            // When
            ShareResponse response = fileSharingService.createShare(1L, shareRequest, testUser);

            // Then
            assertNotNull(response);
            assertNull(response.getMaxAccess());
        }

        @Test
        @DisplayName("Should handle share without expiration")
        void shouldHandleShareWithoutExpiration() {
            // Given
            testShare.setActive(true);
            testShare.setExpiresAt(null);
            testShare.setAccessCount(5);
            testShare.setMaxAccess(10);

            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));

            // When
            Optional<FileShare> result = fileSharingService.validateShareToken("test-token-123");

            // Then
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should handle concurrent access increments")
        void shouldHandleConcurrentAccessIncrements() {
            // Given
            testShare.setActive(true);
            testShare.setExpiresAt(LocalDateTime.now().plusDays(1));
            testShare.setAccessCount(9);
            testShare.setMaxAccess(10);

            when(fileShareRepository.findByShareToken("test-token-123")).thenReturn(Optional.of(testShare));
            when(fileShareRepository.save(testShare)).thenReturn(testShare);

            // When
            boolean result = fileSharingService.recordShareAccess("test-token-123", "192.168.1.1", "TestBrowser/1.0", ShareAccessType.DOWNLOAD);

            // Then
            assertTrue(result);
            assertEquals(10, testShare.getAccessCount());
            verify(shareAccessService).logAccess(testShare, "192.168.1.1", "TestBrowser/1.0", ShareAccessType.DOWNLOAD);
            
            // Subsequent validation should fail due to max access reached
            Optional<FileShare> validationResult = fileSharingService.validateShareToken("test-token-123");
            assertTrue(validationResult.isEmpty());
        }
    }
}