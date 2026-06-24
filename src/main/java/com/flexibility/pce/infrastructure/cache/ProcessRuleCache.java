package com.flexibility.pce.infrastructure.cache;

import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.infrastructure.persistence.query.ProcessRuleQueries;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class ProcessRuleCache {

    private final ProcessRuleQueries ruleQueries;
    private final Cache<String, List<ProcessRule>> cache;

    public ProcessRuleCache(ProcessRuleQueries ruleQueries) {
        this.ruleQueries = ruleQueries;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500)
            .build(this::loadRulesFromDatabase);
    }

    public List<ProcessRule> getActiveRules(String processKey) {
        return cache.get(processKey, k -> ruleQueries.findActiveRulesByProcessKey(k));
    }

    public void invalidate(String processKey) {
        cache.invalidate(processKey);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private List<ProcessRule> loadRulesFromDatabase(String processKey) {
        return ruleQueries.findActiveRulesByProcessKey(processKey);
    }
}
