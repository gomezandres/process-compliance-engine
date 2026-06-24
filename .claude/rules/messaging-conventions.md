---
description: RabbitMQ wiring conventions — queue names, routing keys, and consumer setup
globs: ["src/main/java/**/infrastructure/messaging/**/*.java", "src/main/java/**/config/**/*.java", "src/main/resources/**/*.yml"]
alwaysApply: false
---

# Messaging Conventions

## Queue and routing key

| Property | Value |
|---|---|
| Queue | `process.event.lifecycle` |
| Exchange | `process.events` |
| Exchange type | `topic` |
| Routing key (bind) | `process.lifecycle.*` — matches both `started` and `completed` |
| Prefetch | `1` |
| Auto-ack | `false` — manual acknowledgment only |

Never hardcode these values. They must come from `@Value("${rabbitmq.consumer.*}")` with the defaults above as fallback.

## Consumer wiring — mandatory pattern

**Always** use `RabbitMQConsumer.builder()` from `plug-consumer-rabbitmq`. Never use `@RabbitListener`.

```java
// CORRECT
return RabbitMQConsumer.builder()
    .connectionConfig(connectionConfig)
    .consumerConfig(consumerConfig)
    .messageHandler(TypedEventHandler.forEventMessage(processEventConsumer::handleProcessEvent))
    .build();

// WRONG — forbidden in this project
@RabbitListener(queues = "process.event.lifecycle")
public void handle(EventMessage event) { ... }
```

## Lifecycle of the consumer bean

- `@PostConstruct` calls `rabbitConsumer.connect()` then `rabbitConsumer.startConsuming()`.
- `@PreDestroy` calls `rabbitConsumer.stopConsuming()` then `rabbitConsumer.close()`.
- Both are in `ProcessEventConsumer`, not in the `@Configuration` class.

## Error handling and retry

- `InvalidEventPayloadException` (bad contract) → log ERROR, increment `pce_invalid_events_total`, **do not rethrow** — message is discarded, no retry.
- Any other exception → log ERROR, increment `pce_processing_errors_total`, **rethrow** as `EventProcessingException` so RabbitMQ requeues the message for retry.
- Unknown `event.getType()` → log WARN, increment `pce_unknown_event_type_total`, **do not rethrow** — message is discarded.

## Cache invalidation queue

The `config.sync.queue` is a separate queue consumed by `CacheInvalidationListener` only. It must **not** be handled by `ProcessEventConsumer`. Wire it independently via its own `RabbitMQConsumer` bean.
