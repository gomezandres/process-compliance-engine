package com.flexibility.pce.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class RuleEvaluationResult {
    private boolean violated;
    private String message;
    private Object actualValue;
    private Object expectedValue;
}
