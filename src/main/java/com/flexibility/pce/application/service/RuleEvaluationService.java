package com.flexibility.pce.application.service;

import com.flexibility.pce.domain.entity.EvaluationResult;
import com.flexibility.pce.domain.entity.ProcessDefinition;
import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEngine;
import com.flexibility.pce.infrastructure.persistence.query.ProcessRuleQueries;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuleEvaluationService {

    private final RuleEngine ruleEngine;
    private final ProcessRuleQueries ruleQueries;
    private final MeterRegistry meterRegistry;

    public EvaluationResult evaluate(ProcessExecution execution, ProcessDefinition definition) {
        List<ProcessRule> activeRules = ruleQueries.findActiveRulesByProcessKey(execution.getProcessKey());
        return ruleEngine.evaluateProcess(execution, definition, activeRules,
            new RuleEngine.RuleEngineCallback() {
                public void onViolation(String ruleType, com.flexibility.pce.domain.entity.Severity severity) {
                    meterRegistry.counter("pce_rule_violations_total",
                        "rule_type", ruleType, "severity", severity.toString()).increment();
                }
                public void onEvaluationError(String ruleType) {
                    meterRegistry.counter("pce_rule_evaluation_error", "rule_type", ruleType).increment();
                }
                public void onEvaluationComplete(long durationMs) {
                    meterRegistry.timer("pce_evaluation_duration_seconds")
                        .record(durationMs, TimeUnit.MILLISECONDS);
                }
            });
    }
}
