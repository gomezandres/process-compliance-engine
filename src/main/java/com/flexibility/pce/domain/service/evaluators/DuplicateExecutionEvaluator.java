package com.flexibility.pce.domain.service.evaluators;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEvaluationResult;
import com.flexibility.pce.domain.service.RuleEvaluator;
import com.flexibility.pce.infrastructure.persistence.query.ProcessExecutionQueries;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DuplicateExecutionEvaluator implements RuleEvaluator {

    private final ProcessExecutionQueries executionQueries;

    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        long count = executionQueries.countByProcessKeyAndExecutionId(
            execution.getProcessKey(), execution.getExecutionId());
        boolean isDuplicate = count > 1;
        return new RuleEvaluationResult(isDuplicate,
            isDuplicate ? "Duplicate execution_id detected" : "No duplicate",
            isDuplicate, false);
    }
}
