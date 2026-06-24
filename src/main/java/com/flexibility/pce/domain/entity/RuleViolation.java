package com.flexibility.pce.domain.entity;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class RuleViolation {
    private Long ruleId;
    private String ruleType;
    private Severity severity;
    private String message;
    private Object expectedValue;
    private Object actualValue;
}
