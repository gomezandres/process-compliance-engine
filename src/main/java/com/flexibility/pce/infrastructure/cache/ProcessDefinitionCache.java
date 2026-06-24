package com.flexibility.pce.infrastructure.cache;

import com.flexibility.pce.domain.entity.ProcessDefinition;
import com.flexibility.pce.domain.repository.ProcessDefinitionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class ProcessDefinitionCache {

    private final ProcessDefinitionRepository repository;
    private final Cache<String, ProcessDefinition> cache;
    private final MeterRegistry meterRegistry;

    public ProcessDefinitionCache(ProcessDefinitionRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .recordStats()
            .build(this::loadFromDatabase);
    }

    public Optional<ProcessDefinition> get(String processKey) {
        try {
            ProcessDefinition definition = cache.get(processKey);
            if (definition != null) {
                meterRegistry.counter("pce_cache_hits", "type", "process_definition").increment();
                return Optional.of(definition);
            }
            meterRegistry.counter("pce_cache_misses", "type", "process_definition").increment();
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error accessing cache for processKey: {}", processKey, e);
            meterRegistry.counter("pce_cache_errors", "type", "process_definition").increment();
            return repository.findByProcessKey(processKey);
        }
    }

    public void invalidate(String processKey) {
        cache.invalidate(processKey);
        log.debug("Cache invalidated for processKey: {}", processKey);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private ProcessDefinition loadFromDatabase(String processKey) {
        return repository.findByProcessKey(processKey).orElse(null);
    }

    @Scheduled(fixedDelay = 60000)
    public void reportCacheStats() {
        CacheStats stats = cache.stats();
        log.info("ProcessDefinitionCache stats - Hits: {}, Misses: {}, Hit rate: {:.2f}%",
            stats.hitCount(), stats.missCount(), stats.hitRate() * 100);
    }
}
