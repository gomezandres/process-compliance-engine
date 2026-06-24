package com.flexibility.pce.domain.event;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class ProcessEvent {
    private String eventId;
    private String processKey;
    private String executionId;
    private String eventType;
    private LocalDateTime occurredAt;
}
