package com.flexibility.pce.domain.event;

import com.flexibility.pce.domain.entity.RuleViolation;
import com.flexibility.pce.domain.entity.Severity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class RuleViolationEvent {
    private String processKey;
    private String executionId;
    private RuleViolation violation;
    private Severity severity;
    private LocalDateTime occurredAt;
}
