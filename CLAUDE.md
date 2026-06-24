# process-compliance-engine

## What This Project Is

A **Java 25 / Spring Boot 4** compliance engine that:
1. Consumes process-completion events from RabbitMQ (`process.event.completed`)
2. Evaluates configured rules against each execution (SLA, fail status, duplicates, etc.)
3. Persists execution records in MySQL
4. Publishes Prometheus metrics at `/metrics`

Internal project — Flexibility Tech.

---

## Stack

| Component | Version |
|---|---|
| Java | 25 (virtual threads via Project Loom available) |
| Spring Boot | 4.0.0 |
| Database | MySQL 8 · JPA/Hibernate |
| Cache | Redis |
| Messaging | RabbitMQ via `plug-consumer-rabbitmq 1.0.0-SNAPSHOT` |
| Resilience | Resilience4j (circuit breaker + retry + timeout) |
| Metrics | Micrometer → Prometheus |
| Tests | JUnit 5 |

Build tool: **Maven**. Base package: `com.flexibilitytech.pce`.

---

## Build & Test Commands

```bash
mvn clean install              # compile + test
mvn test                       # tests only
mvn spring-boot:run            # start locally
mvn clean package -DskipTests  # build jar, skip tests
```

---

## Project Structure

```
src/main/java/com/flexibilitytech/pce/
├── application/        # Orchestration: ProcessComplianceService, ProcessExecutionService
│   ├── service/
│   ├── event/          # ProcessCompletionEventPayload, ProcessCompletionEventHandler
│   └── dto/            # EvaluationResultDTO, AlertDTO
├── domain/             # Pure business logic — zero Spring/infrastructure imports
│   ├── entity/         # ProcessDefinition, ProcessRule, ProcessExecution, EvaluationResult
│   ├── service/        # RuleEngine, RuleEvaluator (interface), RuleEvaluatorFactory, evaluators/
│   ├── repository/     # Port interfaces only
│   └── event/          # Domain events
├── infrastructure/     # Adapters: JPA, RabbitMQ, Caffeine, Resilience4j
│   ├── messaging/      # ProcessEventConsumer, PlugEventConsumerConfig
│   ├── persistence/    # JpaProcessXxxRepository, query/ (JPQL helpers)
│   ├── cache/          # ProcessDefinitionCache (TTL 5min), ProcessRuleCache (TTL 10min) — Redis
│   └── resilience/
├── presentation/       # HealthController, PrometheusMetricsRegistry
└── config/             # Spring @Configuration classes
```

Database migration scripts live in `src/main/resources/db/migration/`.

---

## Domain Model

### Tables

| Entity | Table | PK |
|---|---|---|
| `ProcessDefinition` | `process_definition` | `process_key` VARCHAR(120) |
| `ProcessRule` | `process_rule` | `id` BIGINT AUTO_INCREMENT |
| `ProcessExecution` | `process_execution` | `id` BIGINT · UNIQUE `(process_key, execution_id)` |

### Supported Rule Types

`SLA_MAX_MINUTES` · `FAIL_STATUS` · `DUPLICATE_EXECUTION` · `ALLOWED_ERROR_CODES` · `MAX_DURATION_MS` · `MISSED_SCHEDULE`

Each rule has: `rule_type`, `operator` (GT/GTE/LT/LTE/EQ/NEQ/IN), `value_type` (NUMBER/STRING/BOOLEAN/JSON), one threshold field populated, the rest NULL.

### Evaluation Outcome

`EvaluationStatus` = `PASS` | `FAIL`. A FAIL means ≥1 `RuleViolation` was collected. Violations carry `Severity` (INFO / WARNING / CRITICAL).

---

## Architecture Invariants

- **Hexagonal**: `domain/` has zero `@Component`, `@Repository`, `@Autowired`, or infrastructure imports. Implement ports in `infrastructure/`.
- **Evaluator pattern**: every `rule_type` maps to a `RuleEvaluator` bean registered in `RuleEvaluatorFactory` via a single `Map.entry(...)`. Adding a rule type = new class + one line in the factory.
- **No temporal validity on rules**: no `valid_from`/`valid_to`. A rule is active if and only if `enabled = 1`.
- **Idempotency**: `UNIQUE INDEX (process_key, execution_id)` prevents double-processing. Duplicate events are logged as WARN and dropped — not re-evaluated.
- **Cache invalidation**: both Redis caches invalidate on `config.sync.queue` events via `CacheInvalidationListener`.
- **`@Transactional`**: belongs on application-layer services, never on domain services.

---

## Event Contract (plug-event-library)

Incoming `EventMessage` shape:

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

- All code, comments, variables, commits, and docs: **English**.
- Use Lombok `@Data`, `@Builder`, `@Slf4j`. No manual getters/setters/constructors.
- Enums for bounded values: `RuleOperator`, `ValueType`, `Severity`, `ExecutionStatus`, `EvaluationStatus`.
- Custom exceptions extend `ProcessComplianceException`. One class per exception type in `domain/exception/`.
- Metrics prefix: `pce_*` (e.g. `pce_events_received_total`, `pce_rule_violations_total`).
- Logging: `log.info("msg {}", val)`. No string concatenation. No `e.printStackTrace()`.
- Evaluator errors: catch, log error, increment `pce_rule_evaluation_error`, **continue** with remaining rules.

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
