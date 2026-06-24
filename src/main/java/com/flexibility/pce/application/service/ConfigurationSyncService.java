package com.flexibility.pce.application.service;

import com.flexibility.pce.infrastructure.cache.ProcessDefinitionCache;
import com.flexibility.pce.infrastructure.cache.ProcessRuleCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigurationSyncService {

    private final ProcessDefinitionCache definitionCache;
    private final ProcessRuleCache ruleCache;

    public void invalidateProcess(String processKey) {
        log.info("Invalidating configuration cache for process: {}", processKey);
        definitionCache.invalidate(processKey);
        ruleCache.invalidate(processKey);
    }

    public void invalidateAll() {
        log.info("Invalidating all configuration caches");
        definitionCache.invalidateAll();
        ruleCache.invalidateAll();
    }
}
