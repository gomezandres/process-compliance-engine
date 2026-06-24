package com.flexibility.pce.domain.service.evaluators;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEvaluationResult;
import com.flexibility.pce.domain.service.RuleEvaluator;

public class MissedScheduleEvaluator implements RuleEvaluator {

    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        if (execution.getScheduledAt() == null || execution.getStartedAt() == null) {
            return new RuleEvaluationResult(false, "No schedule data available", null, null);
        }
        long delayMinutes = java.time.Duration.between(
            execution.getScheduledAt(), execution.getStartedAt()).toMinutes();
        long graceMinutes = rule.getThresholdNumber().longValue();
        boolean violated = switch (rule.getOperator()) {
            case GT  -> delayMinutes > graceMinutes;
            case GTE -> delayMinutes >= graceMinutes;
            default  -> false;
        };
        return new RuleEvaluationResult(violated,
            String.format("Process started %d minutes late (grace: %d minutes)", delayMinutes, graceMinutes),
            delayMinutes, graceMinutes);
    }
}
