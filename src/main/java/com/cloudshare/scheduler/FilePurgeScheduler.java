package com.cloudshare.scheduler;

import com.cloudshare.model.FileMetadata;
import com.cloudshare.repository.FileRepository;
import com.cloudshare.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FilePurgeScheduler {

    private final FileRepository fileRepository;
    private final FileService fileService;

    @Scheduled(cron = "${app.scheduler.file-purge.cron:0 0 2 * * ?}")
    public void purgeFiles() {
        log.info("Starting file purge scheduler job...");
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<FileMetadata> filesToPurge = fileRepository.findByDeletedTrueAndUpdatedAtBefore(cutoff);
        log.info("Found {} files to permanently purge", filesToPurge.size());

        for (FileMetadata file : filesToPurge) {
            try {
                // Call via proxy to ensure @Transactional(propagation = Propagation.REQUIRES_NEW) works
                fileService.purgeSoftDeletedFile(file);
            } catch (Exception e) {
                log.error("Failed to purge soft-deleted file: id={}, filename={}", file.getId(), file.getOriginalFilename(), e);
            }
        }
        log.info("Finished file purge scheduler job.");
    }
}
