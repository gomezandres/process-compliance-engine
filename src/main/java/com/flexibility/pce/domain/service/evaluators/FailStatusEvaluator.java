package com.flexibility.pce.domain.service.evaluators;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEvaluationResult;
import com.flexibility.pce.domain.service.RuleEvaluator;

public class FailStatusEvaluator implements RuleEvaluator {

    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        String status = execution.getStatus().toString();
        String expectedStatus = rule.getThresholdString();
        boolean violated = switch (rule.getOperator()) {
            case EQ  -> status.equals(expectedStatus);
            case NEQ -> !status.equals(expectedStatus);
            default  -> false;
        };
        return new RuleEvaluationResult(violated,
            String.format("Status violation: expected %s, got %s", expectedStatus, status),
            status, expectedStatus);
    }
}
