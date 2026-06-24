package com.flexibility.pce.application.service;

import com.flexibility.pce.application.dto.AlertDTO;
import com.flexibility.pce.application.event.ProcessLifecycleEventPayload;
import com.flexibility.pce.domain.entity.*;
import com.flexibility.pce.domain.event.ProcessEventPublisher;
import com.flexibility.pce.domain.exception.EventProcessingException;
import com.flexibility.pce.domain.exception.ProcessNotConfiguredException;
import com.flexibility.pce.domain.repository.ProcessDefinitionRepository;
import com.flexibility.pce.domain.service.RuleEngine;
import com.flexibility.pce.infrastructure.persistence.query.ProcessRuleQueries;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessComplianceService {

    private final ProcessDefinitionRepository processDefinitionRepo;
    private final ProcessExecutionService executionService;
    private final RuleEngine ruleEngine;
    private final AlertService alertService;
    private final ProcessEventPublisher eventPublisher;
    private final ProcessRuleQueries ruleQueries;
    private final MeterRegistry meterRegistry;

    @CircuitBreaker(name = "processCompliance", fallbackMethod = "handleStartFallback")
    @Retry(name = "processCompliance")
    @Transactional
    public void processStartEvent(ProcessLifecycleEventPayload payload,
            String eventId, String source, String eventTimestamp) {
        try {
            log.info("Recording start event={} process={} execution={}",
                eventId, payload.getProcessKey(), payload.getExecutionId());

            if (executionService.existsExecution(payload.getProcessKey(), payload.getExecutionId())) {
                log.warn("Duplicate start event ignored: execution={}", payload.getExecutionId());
                meterRegistry.counter("pce_duplicate_executions_total").increment();
                return;
            }
            executionService.recordStart(payload, eventId, source);
        } catch (Exception e) {
            log.error("Error recording start event: {}", eventId, e);
            meterRegistry.counter("pce_processing_errors_total").increment();
            throw new EventProcessingException("Failed to record start event", e);
        }
    }

    @CircuitBreaker(name = "processCompliance", fallbackMethod = "handleCompletionFallback")
    @Retry(name = "processCompliance")
    @Transactional
    public void processCompletionEvent(ProcessLifecycleEventPayload payload,
            String eventId, String source, String eventTimestamp) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Processing completion event={} process={} execution={}",
                eventId, payload.getProcessKey(), payload.getExecutionId());

            ProcessDefinition definition = processDefinitionRepo
                .findByProcessKey(payload.getProcessKey())
                .orElseThrow(() -> new ProcessNotConfiguredException(
                    "Process not found: " + payload.getProcessKey()));

            ProcessExecution execution = executionService
                .completeExecution(payload, definition, eventId, source);

            List<ProcessRule> activeRules = ruleQueries.findActiveRulesByProcessKey(payload.getProcessKey());

            EvaluationResult result = ruleEngine.evaluateProcess(execution, definition, activeRules,
                new RuleEngine.RuleEngineCallback() {
                    public void onViolation(String ruleType, Severity severity) {
                        meterRegistry.counter("pce_rule_violations_total",
                            "rule_type", ruleType, "severity", severity.toString()).increment();
                    }
                    public void onEvaluationError(String ruleType) {
                        meterRegistry.counter("pce_rule_evaluation_error",
                            "rule_type", ruleType).increment();
                    }
                    public void onEvaluationComplete(long durationMs) {
                        meterRegistry.timer("pce_evaluation_duration_seconds")
                            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                });

            meterRegistry.counter("pce_events_evaluated_total",
                "result", result.getStatus().toString()).increment();

            if (result.getStatus() == EvaluationStatus.FAIL) {
                handleViolations(result, definition);
            }

            eventPublisher.publishEvaluationResult(result, source);
            log.info("Completion event {} processed in {}ms", eventId, System.currentTimeMillis() - startTime);

        } catch (ProcessNotConfiguredException e) {
            log.error("Process not configured: {}", payload.getProcessKey());
            meterRegistry.counter("pce_events_unknown_process_total").increment();
            alertService.raiseConfigurationAlert(payload.getProcessKey(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing completion event: {}", eventId, e);
            meterRegistry.counter("pce_processing_errors_total").increment();
            throw new EventProcessingException("Failed to process completion event", e);
        }
    }

    private void handleViolations(EvaluationResult result, ProcessDefinition definition) {
        for (RuleViolation violation : result.getViolations()) {
            AlertDTO alert = AlertDTO.builder()
                .processKey(definition.getProcessKey())
                .severity(violation.getSeverity())
                .message(String.format("[%s] Rule violation in process '%s': %s",
                    violation.getSeverity(), definition.getProcessKey(), violation.getMessage()))
                .ruleType(violation.getRuleType())
                .build();
            alertService.sendAlert(alert);
            if (violation.getSeverity() == Severity.CRITICAL) {
                meterRegistry.counter("pce_critical_alerts_total").increment();
            }
        }
    }

    public void handleStartFallback(ProcessLifecycleEventPayload payload,
            String eventId, String source, String ts, Exception ex) {
        log.error("Circuit breaker activated for start event: {}", eventId, ex);
        meterRegistry.counter("pce_circuit_breaker_activations", "type", "start").increment();
    }

    public void handleCompletionFallback(ProcessLifecycleEventPayload payload,
            String eventId, String source, String ts, Exception ex) {
        log.error("Circuit breaker activated for completion event: {}", eventId, ex);
        meterRegistry.counter("pce_circuit_breaker_activations", "type", "completion").increment();
    }
}
