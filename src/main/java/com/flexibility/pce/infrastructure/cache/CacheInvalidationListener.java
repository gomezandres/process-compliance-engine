package com.flexibility.pce.infrastructure.cache;

import com.flexibility.pce.application.service.ConfigurationSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final ConfigurationSyncService configurationSyncService;

    public void onConfigurationSyncEvent(String processKey) {
        log.info("Cache invalidation triggered for process: {}", processKey);
        configurationSyncService.invalidateProcess(processKey);
    }

    public void onFullSyncEvent() {
        log.info("Full cache invalidation triggered");
        configurationSyncService.invalidateAll();
    }
}
