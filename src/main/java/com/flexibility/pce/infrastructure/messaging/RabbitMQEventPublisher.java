package com.flexibility.pce.infrastructure.messaging;

import com.flexibility.pce.domain.entity.EvaluationResult;
import com.flexibility.pce.domain.event.ProcessEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RabbitMQEventPublisher implements ProcessEventPublisher {

    @Override
    public void publishEvaluationResult(EvaluationResult result, String source) {
        log.info("Evaluation result published: process={} execution={} status={}",
            result.getProcessKey(), result.getExecutionId(), result.getStatus());
    }
}
