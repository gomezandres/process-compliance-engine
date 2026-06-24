package com.flexibility.pce.domain.service;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;

public interface RuleEvaluator {
    RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution);
}
