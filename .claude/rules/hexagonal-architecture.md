---
description: Enforce hexagonal architecture layer boundaries for this project
globs: ["src/main/java/**/*.java"]
alwaysApply: true
---

# Hexagonal Architecture — Layer Boundaries

## Package map

| Package | Role | Spring allowed |
|---|---|---|
| `domain/entity/` | JPA entities + pure value objects | `@Entity`, `@Table`, `@Column`, `@Id` only |
| `domain/service/` | Business logic, RuleEngine, evaluators | NO Spring annotations |
| `domain/repository/` | Port interfaces (no implementation) | NO Spring annotations |
| `domain/exception/` | Exception hierarchy | NO Spring annotations |
| `domain/event/` | Domain event definitions | NO Spring annotations |
| `application/service/` | Orchestration, `@Transactional` owner | `@Service`, `@Transactional` |
| `application/event/` | Payload DTOs, event handlers | `@Component` if needed |
| `application/dto/` | DTOs | none |
| `infrastructure/` | Adapters implementing domain ports | `@Repository`, `@Component` |
| `presentation/` | HTTP controllers, metrics | `@RestController`, `@Component` |
| `config/` | Spring configuration | `@Configuration`, `@Bean` |

## Hard rules

- `domain/` packages must have **zero imports** from `org.springframework`, `jakarta.persistence` (except entity annotations listed above), `com.github.benmanes.caffeine`, `com.rabbitmq`, or any infrastructure library.
- `@Transactional` lives **only** in `application/service/`. Never on domain services, never on infrastructure adapters.
- Domain repository interfaces in `domain/repository/` declare only method signatures — no `extends JpaRepository`, no `@Query`.
- JPA implementations go in `infrastructure/persistence/`. Each one implements the corresponding domain port interface.
- `RuleEvaluator` implementations live in `domain/service/evaluators/`. They are pure logic — no DB calls, no cache access, no Spring beans injected (except via constructor when strictly needed for domain logic).

## Violation examples to avoid

```java
// WRONG — Spring annotation in domain service
@Component
public class RuleEngine { ... }

// WRONG — JPA import in domain repository port
public interface ProcessExecutionRepository extends JpaRepository<ProcessExecution, Long> { ... }

// WRONG — @Transactional on infrastructure adapter
@Repository
@Transactional
public class JpaProcessDefinitionRepository implements ProcessDefinitionRepository { ... }

// CORRECT — @Transactional only in application layer
@Service
@Transactional
public class ProcessComplianceService { ... }
```
