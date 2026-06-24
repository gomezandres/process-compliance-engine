package com.flexibility.pce.application.dto;

import com.flexibility.pce.domain.entity.Severity;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class AlertDTO {
    private String processKey;
    private Severity severity;
    private String message;
    private String ruleType;
}
