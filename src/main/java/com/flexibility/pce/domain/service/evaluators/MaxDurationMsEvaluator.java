package com.flexibility.pce.domain.service.evaluators;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEvaluationResult;
import com.flexibility.pce.domain.service.RuleEvaluator;

public class MaxDurationMsEvaluator implements RuleEvaluator {

    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        if (execution.getDurationMs() == null) {
            return new RuleEvaluationResult(false, "No duration recorded", null, null);
        }
        long duration = execution.getDurationMs();
        long threshold = rule.getThresholdNumber().longValue();
        boolean violated = switch (rule.getOperator()) {
            case GT  -> duration > threshold;
            case GTE -> duration >= threshold;
            case LT  -> duration < threshold;
            case LTE -> duration <= threshold;
            default  -> false;
        };
        return new RuleEvaluationResult(violated,
            String.format("Duration %dms exceeds threshold %dms", duration, threshold),
            duration, threshold);
    }
}
