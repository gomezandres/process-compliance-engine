---
description: Two-phase event lifecycle model — PROCESS_STARTED vs PROCESS_COMPLETED behavior
globs: ["src/main/java/**/application/service/**/*.java", "src/main/java/**/infrastructure/messaging/**/*.java"]
alwaysApply: false
---

# Event Lifecycle — Two-Phase Model

## Overview

The engine consumes two event types from queue `process.event.lifecycle`. Each type triggers a different action. **Rule evaluation happens only on PROCESS_COMPLETED.**

| Event type | Routing key | Action |
|---|---|---|
| `PROCESS_STARTED` | `process.lifecycle.started` | Create `process_execution` with `status=RUNNING`. No rule evaluation. |
| `PROCESS_COMPLETED` | `process.lifecycle.completed` | Update execution record + evaluate all active rules + send alerts if violations. |

## Dispatch in the consumer

The consumer dispatches by `event.getType()`. Do not evaluate type via `payload.getStatus()`.

```java
switch (event.getType()) {
    case "PROCESS_STARTED"   -> complianceService.processStartEvent(...);
    case "PROCESS_COMPLETED" -> complianceService.processCompletionEvent(...);
    default -> {
        log.warn("Unknown event type: {}", event.getType());
        meterRegistry.counter("pce_unknown_event_type_total", "type", event.getType()).increment();
    }
}
```

## Idempotency rules

- **Duplicate PROCESS_STARTED** (same `process_key` + `execution_id`): log WARN, drop, do not create a second record.
- **Duplicate PROCESS_COMPLETED**: log WARN, drop, do not re-evaluate rules.
- Guard: `UNIQUE INDEX (process_key, execution_id)` on `process_execution`.

## Completion without a prior start record

If `PROCESS_COMPLETED` arrives and no `RUNNING` record exists for `(process_key, execution_id)`:

- Create the full record (upsert) before evaluating rules.
- Log WARN so the missing start event is visible.
- Do not skip rule evaluation — treat it as a normal completion.

```java
// in ProcessExecutionService.completeExecution()
return executionRepository
    .findByProcessKeyAndExecutionId(processKey, executionId)
    .map(existing -> updateAndSave(existing, payload))
    .orElseGet(() -> {
        log.warn("No start record found for execution={}, creating full record", executionId);
        return createFullRecord(payload, definition, eventId, source);
    });
```

## Hung execution detection

A periodic job (not the event consumer) queries `process_execution` for records with `status=RUNNING` older than `schedule_grace_minutes`. Those records are marked `UNKNOWN` and generate a WARNING alert. This logic must **not** be added to the event consumer path.

## What NOT to do

- Do not evaluate rules when handling `PROCESS_STARTED`.
- Do not use `@RabbitListener` to split routing — a single consumer handles both types via the switch above.
- Do not create separate queues per event type — both flow through `process.event.lifecycle`.
