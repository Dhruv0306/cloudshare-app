package com.cloudshare.scheduler;

import com.cloudshare.model.FileMetadata;
import com.cloudshare.repository.FileRepository;
import com.cloudshare.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilePurgeSchedulerTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileService fileService;

    private FilePurgeScheduler filePurgeScheduler;

    @BeforeEach
    void setUp() {
        filePurgeScheduler = new FilePurgeScheduler(fileRepository, fileService);
    }

    @Test
    void purgeFiles_noFiles_doesNothing() {
        when(fileRepository.findByDeletedTrueAndUpdatedAtBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        filePurgeScheduler.purgeFiles();

        verify(fileRepository).findByDeletedTrueAndUpdatedAtBefore(any(Instant.class));
        verify(fileService, never()).purgeSoftDeletedFile(any(FileMetadata.class));
    }

    @Test
    void purgeFiles_callsPurgeForEachFileAndContinuesOnFailure() {
        FileMetadata file1 = FileMetadata.builder().id(UUID.randomUUID()).originalFilename("file1.txt").build();
        FileMetadata file2 = FileMetadata.builder().id(UUID.randomUUID()).originalFilename("file2.txt").build();
        List<FileMetadata> files = Arrays.asList(file1, file2);

        when(fileRepository.findByDeletedTrueAndUpdatedAtBefore(any(Instant.class)))
                .thenReturn(files);

        // Make the first file purge fail, second should still be called
        doThrow(new RuntimeException("Storage offline")).when(fileService).purgeSoftDeletedFile(file1);
        doNothing().when(fileService).purgeSoftDeletedFile(file2);

        filePurgeScheduler.purgeFiles();

        verify(fileService).purgeSoftDeletedFile(file1);
        verify(fileService).purgeSoftDeletedFile(file2);
    }
}
