package com.flexibility.pce.domain.event;

import com.flexibility.pce.domain.entity.EvaluationResult;

public interface ProcessEventPublisher {
    void publishEvaluationResult(EvaluationResult result, String source);
}
