package com.flexibility.pce.domain.service;

import com.flexibility.pce.domain.exception.UnsupportedRuleTypeException;
import java.util.Map;

public class RuleEvaluatorFactory {

    private final Map<String, RuleEvaluator> evaluators;

    public RuleEvaluatorFactory(Map<String, RuleEvaluator> evaluators) {
        this.evaluators = Map.copyOf(evaluators);
    }

    public RuleEvaluator getEvaluator(String ruleType) {
        RuleEvaluator evaluator = evaluators.get(ruleType);
        if (evaluator == null) {
            throw new UnsupportedRuleTypeException("No evaluator found for rule type: " + ruleType);
        }
        return evaluator;
    }
}
