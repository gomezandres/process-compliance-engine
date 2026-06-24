# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

A **Java 25 / Spring Boot 4** compliance engine that:
1. Consumes process-completion events from RabbitMQ (`process.event.completed`)
2. Evaluates configured rules against each execution (SLA, fail status, duplicates, etc.)
3. Persists execution records in MySQL
4. Publishes Prometheus metrics at `/metrics`

Internal project — Flexibility Tech. Architecture is fully specified in `custom_docs/`; implementation is in progress.

---

## Stack

| Component | Detail |
|---|---|
| Java | 25 (virtual threads via Project Loom available) |
| Spring Boot | 4.0.0 |
| Database | MySQL 8 · JPA/Hibernate · Flyway migrations |
| Cache | Caffeine (in-process, TTL-based) · invalidated via `config.sync.queue` |
| Messaging | RabbitMQ · `plug-consumer-rabbitmq 1.0.0-SNAPSHOT` |
| Resilience | Resilience4j (circuit breaker + retry + timeout) |
| Metrics | Micrometer → Prometheus at `/metrics` |
| Tests | JUnit 5 |

Build tool: **Maven**. Target base package: `com.flexibilitytech.pce`.

> **Note:** `pom.xml` currently uses `com.flexibility` as `groupId` (Maven archetype default). Update it and the source tree to `com.flexibilitytech` when starting implementation.

---

## Build & Test Commands

```bash
mvn clean install                                    # compile + test
mvn test                                             # tests only
mvn test -Dtest=ClassName                            # single test class
mvn test -Dtest=ClassName#methodName                 # single test method
mvn clean package -DskipTests                        # build jar, skip tests
mvn spring-boot:run                                  # start locally (once Spring Boot is in pom.xml)
```

---

## Project Structure

```
custom_docs/                         # Full design specs — read these before implementing
src/main/java/com/flexibility/pce/
├── application/        # Orchestration: @Transactional lives here only
│   ├── service/        # ProcessComplianceService (main entry), ProcessExecutionService
│   ├── event/          # ProcessCompletionEventPayload, ProcessCompletionEventHandler
│   └── dto/            # EvaluationResultDTO, AlertDTO
├── domain/             # Pure business logic — zero Spring/infrastructure imports
│   ├── entity/         # ProcessDefinition, ProcessRule, ProcessExecution, EvaluationResult
│   ├── service/        # RuleEngine, RuleEvaluator (interface), RuleEvaluatorFactory, evaluators/
│   ├── repository/     # Port interfaces only (no JPA annotations here)
│   ├── exception/      # All extend ProcessComplianceException; one class per type
│   └── event/          # Domain events
├── infrastructure/     # Adapters implementing domain ports
│   ├── messaging/      # ProcessEventConsumer (uses RabbitMQConsumer.builder()), RabbitMQEventPublisher
│   ├── persistence/    # JpaProcessXxxRepository, query/ (JPQL helpers via EntityManager)
│   ├── cache/          # ProcessDefinitionCache (5 min TTL), ProcessRuleCache (10 min TTL), CacheInvalidationListener
│   └── resilience/     # Circuit breaker + retry wrappers
├── presentation/       # HealthController, PrometheusMetricsRegistry
└── config/             # Spring @Configuration classes
src/main/resources/db/migration/     # Flyway scripts (V1_0__init.sql, etc.)
```

---

## Domain Model

| Entity | Table | PK |
|---|---|---|
| `ProcessDefinition` | `process_definition` | `process_key` VARCHAR(120) |
| `ProcessRule` | `process_rule` | `id` BIGINT AUTO_INCREMENT |
| `ProcessExecution` | `process_execution` | `id` BIGINT · UNIQUE `(process_key, execution_id)` |

**Rule types:** `SLA_MAX_MINUTES` · `FAIL_STATUS` · `DUPLICATE_EXECUTION` · `ALLOWED_ERROR_CODES` · `MAX_DURATION_MS` · `MISSED_SCHEDULE`

Each rule has `rule_type`, `operator` (GT/GTE/LT/LTE/EQ/NEQ/IN), `value_type` (NUMBER/STRING/BOOLEAN/JSON), and exactly one threshold field populated.

`EvaluationStatus` = `PASS` | `FAIL`. FAIL means ≥1 `RuleViolation` collected. Violations carry `Severity` (INFO / WARNING / CRITICAL).

---

## Architecture Invariants

- **Hexagonal**: `domain/` has zero `@Component`, `@Repository`, `@Autowired`, or infrastructure imports.
- **Evaluator pattern**: every `rule_type` maps to a `RuleEvaluator` bean registered in `RuleEvaluatorFactory` via `Map.entry(...)`. Adding a rule type = new class + one line in the factory.
- **`@Transactional`**: application-layer services only; never on domain services.
- **Idempotency**: `UNIQUE INDEX (process_key, execution_id)` guards double-processing. Duplicate events → log WARN + drop, no re-evaluation.
- **Evaluator error handling**: catch, log at ERROR, increment `pce_rule_evaluation_error`, **continue** with remaining rules — never abort the evaluation loop on one rule failure.
- **Cache invalidation**: both Caffeine caches listen to `config.sync.queue` via `CacheInvalidationListener` and evict on config-change events.
- **No temporal rule validity**: no `valid_from`/`valid_to`. A rule is active if and only if `enabled = 1`.

---

## Event Contract (plug-event-library)

```json
{
  "eventId": "uuid",
  "timestamp": "2026-06-19T08:12:00.123456Z",
  "source": "asiento-cero-hub",
  "type": "EXECUTE_CRONJOB",
  "contextId": "daily-report-job",
  "payload": {
    "processKey": "asiento-cero-diario",
    "executionId": "exec-20260619-001",
    "status": "COMPLETED",
    "scheduledAt": "2026-06-19T08:00:00",
    "startedAt": "2026-06-19T08:02:30",
    "endedAt": "2026-06-19T08:15:45",
    "durationMs": 789000,
    "errorCode": null,
    "errorMessage": null,
    "metadata": {}
  }
}
```

`processKey`, `executionId`, `status` are **required**. Missing any → `InvalidEventContractException` → discard (no retry). Do NOT use `@RabbitListener` — wire consumers through `plug-consumer-rabbitmq` via `RabbitMQConsumer.builder()`.

---

## Coding Conventions

- Lombok: `@Data`, `@Builder`, `@Slf4j`. No manual getters/setters/constructors.
- Enums for all bounded values: `RuleOperator`, `ValueType`, `Severity`, `ExecutionStatus`, `EvaluationStatus`.
- Metrics prefix `pce_*`: e.g. `pce_events_received_total`, `pce_rule_violations_total`.
- Logging: `log.info("msg {}", val)`. No string concatenation, no `e.printStackTrace()`.

---

## Key Runtime Configuration

```
spring.datasource.url=jdbc:mysql://localhost:3306/process_engine?useSSL=false&serverTimezone=UTC
rabbitmq.consumer.queue=process.event.completed
rabbitmq.consumer.exchange=process.events
rabbitmq.consumer.routing-key=process.completed.*
rabbitmq.consumer.prefetch=1
```

Resilience4j defaults: 50% failure threshold · 30s open-state wait · 3 retry attempts · exponential backoff 500ms base, 2× multiplier · 5s timeout.
