package com.flexibility.pce.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ProcessLifecycleEventPayload {
    private String processKey;
    private String executionId;
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> metadata;
}
