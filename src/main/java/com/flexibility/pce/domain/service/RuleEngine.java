package com.flexibility.pce.domain.service;

import com.flexibility.pce.domain.entity.*;
import com.flexibility.pce.domain.exception.RuleEvaluationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class RuleEngine {

    private final RuleEvaluatorFactory evaluatorFactory;

    public EvaluationResult evaluateProcess(ProcessExecution execution, ProcessDefinition definition,
            List<ProcessRule> activeRules, RuleEngineCallback callback) {

        long startTime = System.currentTimeMillis();
        List<RuleViolation> violations = new ArrayList<>();

        try {
            log.debug("Evaluating {} rules for process: {}", activeRules.size(), execution.getProcessKey());

            for (ProcessRule rule : activeRules) {
                try {
                    RuleEvaluator evaluator = evaluatorFactory.getEvaluator(rule.getRuleType());
                    RuleEvaluationResult result = evaluator.evaluate(rule, execution);

                    if (result.isViolated()) {
                        violations.add(RuleViolation.builder()
                            .ruleId(rule.getId())
                            .ruleType(rule.getRuleType())
                            .severity(rule.getSeverity())
                            .message(result.getMessage())
                            .expectedValue(result.getExpectedValue())
                            .actualValue(result.getActualValue())
                            .build());
                        callback.onViolation(rule.getRuleType(), rule.getSeverity());
                    }
                } catch (Exception e) {
                    log.error("Error evaluating rule: {}", rule.getId(), e);
                    callback.onEvaluationError(rule.getRuleType());
                }
            }

            long evaluationDuration = System.currentTimeMillis() - startTime;
            EvaluationStatus status = violations.isEmpty() ? EvaluationStatus.PASS : EvaluationStatus.FAIL;
            callback.onEvaluationComplete(evaluationDuration);

            return EvaluationResult.builder()
                .executionId(execution.getExecutionId())
                .processKey(execution.getProcessKey())
                .status(status)
                .violations(violations)
                .evaluatedAt(LocalDateTime.now())
                .evaluationDurationMs(evaluationDuration)
                .build();

        } catch (Exception e) {
            log.error("Unexpected error during rule evaluation", e);
            throw new RuleEvaluationException("Failed to evaluate rules", e);
        }
    }

    public interface RuleEngineCallback {
        void onViolation(String ruleType, Severity severity);
        void onEvaluationError(String ruleType);
        void onEvaluationComplete(long durationMs);
    }
}
