package com.flexibility.pce.infrastructure.messaging;

import com.flexibility.pce.application.event.ProcessLifecycleEventPayload;
import com.flexibility.pce.application.service.ProcessComplianceService;
import com.flexibility.pce.domain.exception.EventProcessingException;
import com.flexibility.pce.domain.exception.InvalidEventPayloadException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessEventConsumer {

    private static final String TYPE_STARTED   = "PROCESS_STARTED";
    private static final String TYPE_COMPLETED = "PROCESS_COMPLETED";

    private final ProcessComplianceService complianceService;
    private final com.flexibility.plug.consumer.rabbitmq.RabbitMQConsumer rabbitConsumer;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void start() throws Exception {
        rabbitConsumer.connect();
        rabbitConsumer.startConsuming();
        log.info("ProcessEventConsumer started listening to process.event.lifecycle");
    }

    public void handleProcessEvent(com.flexibility.plug.events.common.EventMessage event) {
        long startTime = System.currentTimeMillis();
        try {
            ProcessLifecycleEventPayload payload = extractPayload(event);

            log.info("Received event type={} source={} process={} execution={}",
                event.getType(), event.getSource(), payload.getProcessKey(), payload.getExecutionId());

            validateRequiredFields(payload);

            switch (event.getType()) {
                case TYPE_STARTED ->
                    complianceService.processStartEvent(
                        payload, event.getEventId(), event.getSource(), event.getTimestamp());
                case TYPE_COMPLETED ->
                    complianceService.processCompletionEvent(
                        payload, event.getEventId(), event.getSource(), event.getTimestamp());
                default -> {
                    log.warn("Unknown event type: {}", event.getType());
                    meterRegistry.counter("pce_unknown_event_type_total", "type", event.getType()).increment();
                }
            }

            meterRegistry.counter("pce_events_received_total", "type", event.getType()).increment();
            log.debug("Event {} processed in {}ms", event.getEventId(), System.currentTimeMillis() - startTime);

        } catch (InvalidEventPayloadException e) {
            log.error("Invalid event payload: {}", e.getMessage());
            meterRegistry.counter("pce_invalid_events_total").increment();
        } catch (Exception e) {
            log.error("Error processing event: {}", event.getEventId(), e);
            meterRegistry.counter("pce_processing_errors_total").increment();
            throw new EventProcessingException("Failed to process event", e);
        }
    }

    private ProcessLifecycleEventPayload extractPayload(com.flexibility.plug.events.common.EventMessage event) {
        try {
            Map<String, Object> payload = event.getPayload();
            return ProcessLifecycleEventPayload.builder()
                .processKey((String) payload.get("processKey"))
                .executionId((String) payload.get("executionId"))
                .status((String) payload.get("status"))
                .scheduledAt(parseDateTime((String) payload.get("scheduledAt")))
                .startedAt(parseDateTime((String) payload.get("startedAt")))
                .endedAt(parseDateTime((String) payload.get("endedAt")))
                .durationMs(toLong(payload.get("durationMs")))
                .errorCode((String) payload.get("errorCode"))
                .errorMessage((String) payload.get("errorMessage"))
                .metadata((Map<String, Object>) payload.get("metadata"))
                .build();
        } catch (Exception e) {
            throw new InvalidEventPayloadException("Failed to extract payload: " + e.getMessage(), e);
        }
    }

    private void validateRequiredFields(ProcessLifecycleEventPayload payload) {
        if (payload.getProcessKey() == null || payload.getProcessKey().isBlank()) {
            throw new InvalidEventPayloadException("processKey is required");
        }
        if (payload.getExecutionId() == null || payload.getExecutionId().isBlank()) {
            throw new InvalidEventPayloadException("executionId is required");
        }
        if (payload.getStatus() == null || payload.getStatus().isBlank()) {
            throw new InvalidEventPayloadException("status is required");
        }
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null) return null;
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return null;
    }

    @PreDestroy
    public void stop() throws Exception {
        if (rabbitConsumer != null) {
            rabbitConsumer.stopConsuming();
            rabbitConsumer.close();
            log.info("ProcessEventConsumer stopped");
        }
    }
}
