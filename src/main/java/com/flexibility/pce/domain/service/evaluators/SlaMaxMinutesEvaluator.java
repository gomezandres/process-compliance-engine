package com.flexibility.pce.domain.service.evaluators;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEvaluationResult;
import com.flexibility.pce.domain.service.RuleEvaluator;

public class SlaMaxMinutesEvaluator implements RuleEvaluator {

    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        if (execution.getDurationMs() == null) {
            return new RuleEvaluationResult(false, "No duration recorded", null, null);
        }
        long durationMinutes = execution.getDurationMs() / 60_000;
        long threshold = rule.getThresholdNumber().longValue();
        boolean violated = switch (rule.getOperator()) {
            case GT  -> durationMinutes > threshold;
            case GTE -> durationMinutes >= threshold;
            case LT  -> durationMinutes < threshold;
            case LTE -> durationMinutes <= threshold;
            default  -> false;
        };
        return new RuleEvaluationResult(violated,
            String.format("SLA violation: %d minutes exceeds threshold %d", durationMinutes, threshold),
            durationMinutes, threshold);
    }
}
