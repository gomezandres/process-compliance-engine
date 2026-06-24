package com.flexibility.pce.application.service;

import com.flexibility.pce.application.event.ProcessLifecycleEventPayload;
import com.flexibility.pce.domain.entity.ExecutionStatus;
import com.flexibility.pce.domain.entity.ProcessDefinition;
import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.repository.ProcessExecutionRepository;
import com.flexibility.pce.infrastructure.persistence.query.ProcessExecutionQueries;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessExecutionService {

    private final ProcessExecutionRepository executionRepository;
    private final ProcessExecutionQueries executionQueries;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProcessExecution recordStart(ProcessLifecycleEventPayload payload, String eventId, String source) {
        ProcessExecution execution = ProcessExecution.builder()
            .executionId(payload.getExecutionId())
            .processKey(payload.getProcessKey())
            .scheduledAt(payload.getScheduledAt())
            .startedAt(payload.getStartedAt())
            .status(ExecutionStatus.RUNNING)
            .metadata(serializeMetadata(payload.getMetadata(), eventId, source))
            .build();
        return executionRepository.save(execution);
    }

    @Transactional
    public ProcessExecution completeExecution(ProcessLifecycleEventPayload payload,
            ProcessDefinition definition, String eventId, String source) {
        return executionRepository
            .findByProcessKeyAndExecutionId(payload.getProcessKey(), payload.getExecutionId())
            .map(existing -> {
                existing.setEndedAt(payload.getEndedAt());
                existing.setDurationMs(payload.getDurationMs());
                existing.setStatus(ExecutionStatus.valueOf(payload.getStatus()));
                existing.setErrorCode(payload.getErrorCode());
                existing.setErrorMessage(payload.getErrorMessage());
                return executionRepository.save(existing);
            })
            .orElseGet(() -> {
                log.warn("No start record found for execution={}, creating full record", payload.getExecutionId());
                ProcessExecution execution = ProcessExecution.builder()
                    .executionId(payload.getExecutionId())
                    .processKey(definition.getProcessKey())
                    .scheduledAt(payload.getScheduledAt())
                    .startedAt(payload.getStartedAt())
                    .endedAt(payload.getEndedAt())
                    .durationMs(payload.getDurationMs())
                    .status(ExecutionStatus.valueOf(payload.getStatus()))
                    .errorCode(payload.getErrorCode())
                    .errorMessage(payload.getErrorMessage())
                    .metadata(serializeMetadata(payload.getMetadata(), eventId, source))
                    .build();
                return executionRepository.save(execution);
            });
    }

    public boolean existsExecution(String processKey, String executionId) {
        return executionQueries.existsExecutionId(processKey, executionId);
    }

    public Optional<ProcessExecution> getLastExecution(String processKey) {
        return executionQueries.findLastExecutionByProcessKey(processKey);
    }

    private String serializeMetadata(Map<String, Object> payload, String eventId, String source) {
        try {
            Map<String, Object> metadata = payload != null ? new HashMap<>(payload) : new HashMap<>();
            metadata.put("_eventId", eventId);
            metadata.put("_source", source);
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("Error serializing metadata", e);
            return "{}";
        }
    }
}
