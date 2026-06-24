package com.flexibility.pce.application.dto;

import com.flexibility.pce.domain.entity.EvaluationStatus;
import com.flexibility.pce.domain.entity.RuleViolation;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class EvaluationResultDTO {
    private String executionId;
    private String processKey;
    private EvaluationStatus status;
    private List<RuleViolation> violations;
    private LocalDateTime evaluatedAt;
    private Long evaluationDurationMs;
}
