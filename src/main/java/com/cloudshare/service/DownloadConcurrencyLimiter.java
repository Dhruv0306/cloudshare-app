package com.cloudshare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
@Slf4j
public class DownloadConcurrencyLimiter {

    private volatile Semaphore permits;

    public DownloadConcurrencyLimiter(
            @Value("${storage.max-concurrent-decrypt-downloads:20}") int maxConcurrent) {
        this.permits = new Semaphore(maxConcurrent, true);
    }

    public Semaphore getSemaphore() {
        return this.permits;
    }

    public synchronized void setMaxConcurrentDownloads(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Concurrency limit must be at least 1");
        }
        log.info("Updating download concurrency limit to {}", limit);
        this.permits = new Semaphore(limit, true);
    }
}
