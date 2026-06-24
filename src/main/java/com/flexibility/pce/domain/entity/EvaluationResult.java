package com.flexibility.pce.domain.entity;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class EvaluationResult {

    private String executionId;
    private String processKey;
    private EvaluationStatus status;
    private List<RuleViolation> violations;
    private LocalDateTime evaluatedAt;
    private Long evaluationDurationMs;

    public boolean hasCriticalViolations() {
        return violations.stream().anyMatch(v -> v.getSeverity() == Severity.CRITICAL);
    }

    public boolean hasWarnings() {
        return violations.stream().anyMatch(v -> v.getSeverity() == Severity.WARNING);
    }
}
