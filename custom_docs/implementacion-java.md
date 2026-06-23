# Implementación en Java - process-compliance-engine

## 1. Visión General

Sistema modular y escalable diseñado para evaluar 100+ procesos de forma robusta y con latencia baja. Utiliza patrones de arquitectura hexagonal, inyección de dependencias y procesamiento asincrónico.

Stack objetivo de implementación:

- Java 25
- Spring Boot 4

### Principios de diseño

- **Single Responsibility Principle**: Cada componente tiene una razón para cambiar.
- **Escalabilidad horizontal**: Múltiples instancias del motor pueden ejecutarse en paralelo.
- **Resiliencia**: Circuit breakers, reintentos, timeouts.
- **Performance**: Caché en memoria, índices de consultas, batch processing.
- **Testabilidad**: Separación clara entre lógica de negocio e infraestructura.

---

## 2. Estructura del Proyecto

```
process-compliance-engine/
├── src/main/java/com/flexibilitytech/pce/
│   ├── application/
│   │   ├── service/
│   │   │   ├── ProcessComplianceService.java
│   │   │   ├── RuleEvaluationService.java
│   │   │   ├── ProcessExecutionService.java
│   │   │   └── ConfigurationSyncService.java
│   │   ├── event/
│   │   │   ├── ProcessCompletionEventPayload.java
│   │   │   └── ProcessCompletionEventHandler.java
│   │   └── dto/
│   │       ├── EvaluationResultDTO.java
│   │       └── AlertDTO.java
│   ├── domain/
│   │   ├── entity/
│   │   │   ├── ProcessDefinition.java
│   │   │   ├── ProcessRule.java
│   │   │   ├── ProcessExecution.java
│   │   │   └── EvaluationResult.java
│   │   ├── service/
│   │   │   ├── RuleEngine.java
│   │   │   ├── RuleEvaluator.java
│   │   │   └── ProcessValidator.java
│   │   ├── repository/
│   │   │   ├── ProcessDefinitionRepository.java
│   │   │   ├── ProcessRuleRepository.java
│   │   │   └── ProcessExecutionRepository.java
│   │   └── event/
│   │       ├── ProcessEvent.java
│   │       ├── ProcessEventPublisher.java
│   │       └── RuleViolationEvent.java
│   ├── infrastructure/
│   │   ├── messaging/
│   │   │   ├── ProcessEventConsumer.java
│   │   │   ├── RabbitMQEventPublisher.java
│   │   │   └── PlugEventConsumerConfig.java
│   │   ├── persistence/
│   │   │   ├── JpaProcessDefinitionRepository.java
│   │   │   ├── JpaProcessRuleRepository.java
│   │   │   ├── JpaProcessExecutionRepository.java
│   │   │   └── query/
│   │   │       ├── ProcessExecutionQueries.java
│   │   │       └── ProcessRuleQueries.java
│   │   ├── cache/
│   │   │   ├── ProcessDefinitionCache.java
│   │   │   └── ProcessRuleCache.java
│   │   └── resilience/
│   │       ├── DatabaseCircuitBreaker.java
│   │       └── RabbitMQCircuitBreaker.java
│   ├── presentation/
│   │   ├── controller/
│   │   │   └── HealthController.java
│   │   └── metrics/
│   │       └── PrometheusMetricsRegistry.java
│   └── config/
│       ├── PlugEventConsumerConfig.java
│       ├── CacheConfig.java
│       ├── JpaConfig.java
│       └── ResilienceConfig.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
├── src/test/java/...
└── pom.xml
```

---

## 3. Modelos de Dominio

### 3.1 Entidades de Dominio

```java
// domain/entity/ProcessDefinition.java
@Entity
@Table(name = "process_definition")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessDefinition {
    
    @Id
    @Column(name = "process_key")
    private String processKey;
    
    @Column(nullable = false)
    private Boolean active;
    
    @Column(nullable = false)
    private String scheduleCron;
    
    @Column(nullable = false)
    private String scheduleTimezone;
    
    @Column(nullable = false)
    private Integer scheduleGraceMinutes;
    
    @Column
    private String owner;
    
    @Column
    private String description;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "processKey", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ProcessRule> rules = new HashSet<>();
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
```

```java
// domain/entity/ProcessRule.java
@Entity
@Table(name = "process_rule", indexes = {
    @Index(name = "idx_rule_process_enabled", columnList = "process_key, enabled")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String processKey;
    
    @Column(nullable = false)
    private String ruleType;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleOperator operator;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ValueType valueType;
    
    @Column
    private BigDecimal thresholdNumber;
    
    @Column
    private String thresholdString;
    
    @Column
    private Boolean thresholdBoolean;
    
    @Column(columnDefinition = "JSON")
    private String thresholdJson;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;
    
    @Column(nullable = false)
    private Boolean enabled;
    
    @Column
    private String description;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}

public enum RuleOperator {
    GT, GTE, LT, LTE, EQ, NEQ, IN
}

public enum ValueType {
    NUMBER, STRING, BOOLEAN, JSON
}

public enum Severity {
    INFO, WARNING, CRITICAL
}
```

```java
// domain/entity/ProcessExecution.java
@Entity
@Table(name = "process_execution", indexes = {
    @Index(name = "idx_execution_process", columnList = "process_key"),
    @Index(name = "idx_execution_id", columnList = "execution_id"),
    @Index(name = "idx_execution_created", columnList = "process_key, created_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String executionId;
    
    @Column(nullable = false)
    private String processKey;
    
    @Column
    private LocalDateTime scheduledAt;
    
    @Column
    private LocalDateTime startedAt;
    
    @Column
    private LocalDateTime endedAt;
    
    @Column
    private Long durationMs;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;
    
    @Column
    private String errorCode;
    
    @Column
    private String errorMessage;
    
    @Column(columnDefinition = "JSON")
    private String metadata;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

public enum ExecutionStatus {
    RUNNING, COMPLETED, FAILED, SKIPPED, UNKNOWN
}
```

```java
// domain/entity/EvaluationResult.java
@Data
@Builder
public class EvaluationResult {
    
    private String executionId;
    private String processKey;
    private EvaluationStatus status; // PASS, FAIL
    private List<RuleViolation> violations;
    private LocalDateTime evaluatedAt;
    private Long evaluationDurationMs;
    
    public boolean hasCriticalViolations() {
        return violations.stream()
            .anyMatch(v -> v.getSeverity() == Severity.CRITICAL);
    }
    
    public boolean hasWarnings() {
        return violations.stream()
            .anyMatch(v -> v.getSeverity() == Severity.WARNING);
    }
}

@Data
@Builder
public class RuleViolation {
    private Long ruleId;
    private String ruleType;
    private Severity severity;
    private String message;
    private Object expectedValue;
    private Object actualValue;
}

public enum EvaluationStatus {
    PASS, FAIL
}
```

---

## 4. Repositorios y Queries Optimizadas

### 4.1 Repositorios Base

```java
// domain/repository/ProcessDefinitionRepository.java
public interface ProcessDefinitionRepository {
    Optional<ProcessDefinition> findByProcessKey(String processKey);
    List<ProcessDefinition> findAllActive();
    void save(ProcessDefinition definition);
}

// infrastructure/persistence/JpaProcessDefinitionRepository.java
@Repository
public class JpaProcessDefinitionRepository 
    implements ProcessDefinitionRepository {
    
    private final ProcessDefinitionJpaRepository jpaRepository;
    
    @Override
    public Optional<ProcessDefinition> findByProcessKey(String processKey) {
        return jpaRepository.findById(processKey);
    }
    
    @Override
    public List<ProcessDefinition> findAllActive() {
        return jpaRepository.findAllByActiveTrue();
    }
    
    @Override
    public void save(ProcessDefinition definition) {
        jpaRepository.save(definition);
    }
}

@Repository
interface ProcessDefinitionJpaRepository 
    extends JpaRepository<ProcessDefinition, String> {
    
    @Query("SELECT p FROM ProcessDefinition p WHERE p.active = true")
    List<ProcessDefinition> findAllByActiveTrue();
}
```

### 4.2 Queries Especializadas

```java
// infrastructure/persistence/query/ProcessRuleQueries.java
@Component
public class ProcessRuleQueries {
    
    private final EntityManager entityManager;
    
    /**
     * Obtiene todas las reglas activas para un proceso.
     * Optimizado con proyección para evitar cargar campos innecesarios.
     */
    public List<ProcessRule> findActiveRulesByProcessKey(String processKey) {
        return entityManager.createQuery(
            "SELECT r FROM ProcessRule r " +
            "WHERE r.processKey = :processKey " +
            "  AND r.enabled = true " +
            "ORDER BY r.id ASC",
            ProcessRule.class)
            .setParameter("processKey", processKey)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();
    }
    
    /**
     * Obtiene la última ejecución registrada para un proceso.
     * Crítica para validar MISSED_SCHEDULE.
     */
    public Optional<ProcessExecution> findLastExecutionByProcessKey(String processKey) {
        List<ProcessExecution> result = entityManager.createQuery(
            "SELECT e FROM ProcessExecution e " +
            "WHERE e.processKey = :processKey " +
            "ORDER BY e.createdAt DESC",
            ProcessExecution.class)
            .setParameter("processKey", processKey)
            .setMaxResults(1)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();
        
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
    
    /**
     * Obtiene ejecuciones fallidas en los últimos N días.
     * Usado para análisis de patrones y regla DUPLICATE_EXECUTION.
     */
    public List<ProcessExecution> findFailedExecutionsInLast(
        String processKey, int days) {
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        return entityManager.createQuery(
            "SELECT e FROM ProcessExecution e " +
            "WHERE e.processKey = :processKey " +
            "  AND e.status = 'FAILED' " +
            "  AND e.createdAt >= :since " +
            "ORDER BY e.createdAt DESC",
            ProcessExecution.class)
            .setParameter("processKey", processKey)
            .setParameter("since", since)
            .getResultList();
    }
    
    /**
     * Verifica si ya existe un execution_id para un proceso (deduplicación).
     */
    public boolean existsExecutionId(String processKey, String executionId) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(e) FROM ProcessExecution e " +
            "WHERE e.processKey = :processKey " +
            "  AND e.executionId = :executionId",
            Long.class)
            .setParameter("processKey", processKey)
            .setParameter("executionId", executionId)
            .getSingleResult();
        
        return count > 0;
    }
}
```

---

## 5. Lógica del Dominio - RuleEngine

### 5.1 Evaluador de Reglas Base

```java
// domain/service/RuleEvaluator.java
public interface RuleEvaluator {
    RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution);
}

@Data
@AllArgsConstructor
public class RuleEvaluationResult {
    private boolean violated;
    private String message;
    private Object actualValue;
    private Object expectedValue;
}
```

### 5.2 Evaluadores Concretos

```java
// domain/service/evaluators/SlaMaxMinutesEvaluator.java
@Component
public class SlaMaxMinutesEvaluator implements RuleEvaluator {
    
    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        
        if (execution.getDurationMs() == null) {
            return new RuleEvaluationResult(false, "No duration recorded", null, null);
        }
        
        long durationMinutes = execution.getDurationMs() / 60_000;
        long threshold = rule.getThresholdNumber().longValue();
        
        boolean violated = switch (rule.getOperator()) {
            case GT -> durationMinutes > threshold;
            case GTE -> durationMinutes >= threshold;
            case LT -> durationMinutes < threshold;
            case LTE -> durationMinutes <= threshold;
            default -> false;
        };
        
        return new RuleEvaluationResult(
            violated,
            String.format("SLA violation: %d minutes exceeds threshold %d", 
                durationMinutes, threshold),
            durationMinutes,
            threshold
        );
    }
}

// domain/service/evaluators/FailStatusEvaluator.java
@Component
public class FailStatusEvaluator implements RuleEvaluator {
    
    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        
        String status = execution.getStatus().toString();
        String expectedStatus = rule.getThresholdString();
        
        boolean violated = switch (rule.getOperator()) {
            case EQ -> status.equals(expectedStatus);
            case NEQ -> !status.equals(expectedStatus);
            default -> false;
        };
        
        return new RuleEvaluationResult(
            violated,
            String.format("Status violation: expected %s, got %s", 
                expectedStatus, status),
            status,
            expectedStatus
        );
    }
}

// domain/service/evaluators/DuplicateExecutionEvaluator.java
@Component
public class DuplicateExecutionEvaluator implements RuleEvaluator {
    
    private final ProcessExecutionQueries executionQueries;
    
    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        
        long countWithSameId = executionQueries
            .countByProcessKeyAndExecutionId(
                execution.getProcessKey(), 
                execution.getExecutionId());
        
        boolean isDuplicate = countWithSameId > 1;
        
        return new RuleEvaluationResult(
            isDuplicate,
            isDuplicate ? "Duplicate execution_id detected" : "No duplicate",
            isDuplicate,
            false
        );
    }
}

// domain/service/evaluators/AllowedErrorCodesEvaluator.java
@Component
public class AllowedErrorCodesEvaluator implements RuleEvaluator {
    
    private final JsonMapper jsonMapper;
    
    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        
        String errorCode = execution.getErrorCode();
        if (errorCode == null) {
            return new RuleEvaluationResult(false, "No error code", null, null);
        }
        
        List<String> allowedCodes = jsonMapper.parseJsonArray(rule.getThresholdJson());
        boolean isAllowed = allowedCodes.contains(errorCode);
        
        return new RuleEvaluationResult(
            !isAllowed,
            String.format("Error code %s not in allowed list", errorCode),
            errorCode,
            allowedCodes
        );
    }
}
```

### 5.3 Factory de Evaluadores

```java
// domain/service/RuleEvaluatorFactory.java
@Component
public class RuleEvaluatorFactory {
    
    private final Map<String, RuleEvaluator> evaluators;
    
    @Autowired
    public RuleEvaluatorFactory(
        SlaMaxMinutesEvaluator slaEvaluator,
        FailStatusEvaluator statusEvaluator,
        DuplicateExecutionEvaluator duplicateEvaluator,
        AllowedErrorCodesEvaluator errorCodesEvaluator,
        MaxDurationMsEvaluator durationEvaluator,
        MissedScheduleEvaluator scheduleEvaluator) {
        
        this.evaluators = Map.ofEntries(
            Map.entry("SLA_MAX_MINUTES", slaEvaluator),
            Map.entry("FAIL_STATUS", statusEvaluator),
            Map.entry("DUPLICATE_EXECUTION", duplicateEvaluator),
            Map.entry("ALLOWED_ERROR_CODES", errorCodesEvaluator),
            Map.entry("MAX_DURATION_MS", durationEvaluator),
            Map.entry("MISSED_SCHEDULE", scheduleEvaluator)
        );
    }
    
    public RuleEvaluator getEvaluator(String ruleType) 
        throws UnsupportedRuleTypeException {
        
        RuleEvaluator evaluator = evaluators.get(ruleType);
        if (evaluator == null) {
            throw new UnsupportedRuleTypeException(
                "No evaluator found for rule type: " + ruleType);
        }
        return evaluator;
    }
}
```

### 5.4 Motor de Reglas Principal

```java
// domain/service/RuleEngine.java
@Component
@Slf4j
public class RuleEngine {
    
    private final RuleEvaluatorFactory evaluatorFactory;
    private final ProcessRuleQueries ruleQueries;
    private final ProcessExecutionQueries executionQueries;
    private final MeterRegistry meterRegistry;
    
    /**
     * Evalúa todas las reglas activas para un proceso.
     * Retorna EvaluationResult con todas las violaciones detectadas.
     */
    public EvaluationResult evaluateProcess(
        ProcessExecution execution,
        ProcessDefinition definition) {
        
        long startTime = System.currentTimeMillis();
        List<RuleViolation> violations = new ArrayList<>();
        
        try {
            // 1. Obtener reglas activas desde cache
            List<ProcessRule> activeRules = ruleQueries
                .findActiveRulesByProcessKey(execution.getProcessKey());
            
            log.debug("Evaluating {} rules for process: {}",
                activeRules.size(), execution.getProcessKey());
            
            // 2. Evaluar cada regla
            for (ProcessRule rule : activeRules) {
                try {
                    RuleEvaluator evaluator = evaluatorFactory
                        .getEvaluator(rule.getRuleType());
                    
                    RuleEvaluationResult result = evaluator
                        .evaluate(rule, execution);
                    
                    if (result.isViolated()) {
                        violations.add(RuleViolation.builder()
                            .ruleId(rule.getId())
                            .ruleType(rule.getRuleType())
                            .severity(rule.getSeverity())
                            .message(result.getMessage())
                            .expectedValue(result.getExpectedValue())
                            .actualValue(result.getActualValue())
                            .build());
                        
                        recordRuleViolation(rule.getRuleType(), rule.getSeverity());
                    }
                    
                } catch (Exception e) {
                    log.error("Error evaluating rule: {}", rule.getId(), e);
                    // Registrar pero continuar con otras reglas
                    meterRegistry.counter(
                        "pce_rule_evaluation_error",
                        "rule_type", rule.getRuleType()
                    ).increment();
                }
            }
            
            // 3. Construir resultado
            long evaluationDuration = System.currentTimeMillis() - startTime;
            
            EvaluationStatus status = violations.isEmpty() 
                ? EvaluationStatus.PASS 
                : EvaluationStatus.FAIL;
            
            meterRegistry.timer("pce_evaluation_duration_seconds")
                .record(evaluationDuration, TimeUnit.MILLISECONDS);
            
            return EvaluationResult.builder()
                .executionId(execution.getExecutionId())
                .processKey(execution.getProcessKey())
                .status(status)
                .violations(violations)
                .evaluatedAt(LocalDateTime.now())
                .evaluationDurationMs(evaluationDuration)
                .build();
            
        } catch (Exception e) {
            log.error("Unexpected error during rule evaluation", e);
            throw new RuleEvaluationException("Failed to evaluate rules", e);
        }
    }
    
    private void recordRuleViolation(String ruleType, Severity severity) {
        meterRegistry.counter(
            "pce_rule_violations_total",
            "rule_type", ruleType,
            "severity", severity.toString()
        ).increment();
    }
}
```

---

## 6. Servicios de Aplicación

### 6.1 ProcessComplianceService (Orquestador)

```java
// application/service/ProcessComplianceService.java
@Service
@Slf4j
@Transactional
public class ProcessComplianceService {
    
    private final ProcessDefinitionRepository processDefinitionRepo;
    private final ProcessExecutionService executionService;
    private final RuleEngine ruleEngine;
    private final AlertService alertService;
    private final ProcessEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    
    /**
     * Punto de entrada principal: procesa un evento de finalización.
     * Implementa lógica de resiliencia y deduplicación.
     */
    @CircuitBreaker(name = "processCompliance", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "processCompliance")
    @Timeout(value = "5s")
    public void processCompletionEvent(
        ProcessCompletionEventPayload payload,
        String eventId,
        String source,
        String eventTimestamp) {
        
        long startTime = System.currentTimeMillis();
        meterRegistry.counter("pce_events_received_total").increment();
        
        try {
            log.info("Processing event {} from source: {}, process: {}, execution: {}",
                eventId, source, payload.getProcessKey(), payload.getExecutionId());
            
            // 1. Validar contrato del evento
            validatePayload(payload);
            
            // 2. Verificar deduplicación
            if (executionService.existsExecution(
                payload.getProcessKey(), 
                payload.getExecutionId())) {
                
                log.warn("Duplicate execution detected: {}", payload.getExecutionId());
                meterRegistry.counter("pce_duplicate_executions_total").increment();
                return;
            }
            
            // 3. Cargar definición del proceso
            ProcessDefinition definition = processDefinitionRepo
                .findByProcessKey(payload.getProcessKey())
                .orElseThrow(() -> new ProcessNotConfiguredException(
                    "Process not found: " + payload.getProcessKey()));
            
            // 4. Registrar ejecución con metadata del evento
            ProcessExecution execution = executionService
                .recordExecution(payload, definition, eventId, source);
            
            // 5. Evaluar reglas
            EvaluationResult result = ruleEngine.evaluateProcess(
                execution, definition);
            
            meterRegistry.counter(
                "pce_events_evaluated_total",
                "result", result.getStatus().toString()
            ).increment();
            
            // 6. Disparar alertas si hay violaciones
            if (result.getStatus() == EvaluationStatus.FAIL) {
                handleViolations(result, definition);
            }
            
            // 7. Publicar evento de resultado
            eventPublisher.publishEvaluationResult(result, source);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Event {} processed successfully in {}ms", eventId, duration);
            
        } catch (ProcessNotConfiguredException e) {
            log.error("Process not configured: {}", payload.getProcessKey());
            meterRegistry.counter("pce_events_unknown_process_total").increment();
            alertService.raiseConfigurationAlert(payload.getProcessKey(), e.getMessage());
            
        } catch (Exception e) {
            log.error("Unexpected error processing event: {}", eventId, e);
            meterRegistry.counter("pce_processing_errors_total").increment();
            throw new EventProcessingException("Failed to process event", e);
        }
    }
    
    private void validatePayload(ProcessCompletionEventPayload payload) {
        if (payload.getProcessKey() == null || payload.getProcessKey().isBlank()) {
            throw new InvalidEventContractException("processKey is required");
        }
        if (payload.getExecutionId() == null || payload.getExecutionId().isBlank()) {
            throw new InvalidEventContractException("executionId is required");
        }
        if (payload.getStatus() == null) {
            throw new InvalidEventContractException("status is required");
        }
    }
    
    private void handleViolations(EvaluationResult result, 
        ProcessDefinition definition) {
        
        for (RuleViolation violation : result.getViolations()) {
            
            String message = String.format(
                "[%s] Rule violation in process '%s': %s",
                violation.getSeverity(),
                definition.getProcessKey(),
                violation.getMessage()
            );
            
            AlertDTO alert = AlertDTO.builder()
                .processKey(definition.getProcessKey())
                .severity(violation.getSeverity())
                .message(message)
                .ruleType(violation.getRuleType())
                .build();
            
            alertService.sendAlert(alert);
            
            if (violation.getSeverity() == Severity.CRITICAL) {
                meterRegistry.counter("pce_critical_alerts_total").increment();
            }
        }
    }
    
    // Fallback para circuit breaker
    public void handleCircuitBreakerFallback(
        ProcessCompletionEventPayload payload, 
        String eventId,
        String source,
        String eventTimestamp,
        Exception ex) {
        
        log.error("Circuit breaker activated for event: {}", eventId, ex);
        // Enqueuer el evento para reintentar más tarde
        meterRegistry.counter("pce_circuit_breaker_activations").increment();
    }
}
```

### 6.2 ProcessExecutionService (Persistencia)

```java
// application/service/ProcessExecutionService.java
@Service
@Slf4j
public class ProcessExecutionService {
    
    private final ProcessExecutionRepository executionRepository;
    private final ProcessExecutionQueries executionQueries;
    
    @Transactional
    public ProcessExecution recordExecution(
        ProcessCompletionEventPayload payload,
        ProcessDefinition definition,
        String eventId,
        String source) {
        
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
    }
    
    public boolean existsExecution(String processKey, String executionId) {
        return executionQueries.existsExecutionId(processKey, executionId);
    }
    
    public Optional<ProcessExecution> getLastExecution(String processKey) {
        return executionQueries.findLastExecutionByProcessKey(processKey);
    }
    
    /**
     * Serializar metadata incluyendo información de trazabilidad.
     */
    private String serializeMetadata(Map<String, Object> payload, 
        String eventId, String source) {
        
        try {
            Map<String, Object> metadata = payload != null 
                ? new HashMap<>(payload) 
                : new HashMap<>();
            
            metadata.put("_eventId", eventId);
            metadata.put("_source", source);
            metadata.put("_recordedAt", LocalDateTime.now().toString());
            
            return new ObjectMapper().writeValueAsString(metadata);
            
        } catch (Exception e) {
            log.error("Error serializing metadata", e);
            return "{}";
        }
    }
}
```
        return executionQueries.findLastExecutionByProcessKey(processKey);
    }
}
```

---

## 7. Caché y Escalabilidad

### 7.1 Estrategia de Caché

```java
// infrastructure/cache/ProcessDefinitionCache.java
@Component
@Slf4j
public class ProcessDefinitionCache {
    
    private final ProcessDefinitionRepository repository;
    private final Cache<String, ProcessDefinition> cache;
    private final MeterRegistry meterRegistry;
    
    public ProcessDefinitionCache(ProcessDefinitionRepository repository,
        MeterRegistry meterRegistry) {
        
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        
        // Caché con TTL de 5 minutos y máximo 1000 entradas
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .recordStats()
            .build(key -> loadFromDatabase(key));
    }
    
    public Optional<ProcessDefinition> get(String processKey) {
        try {
            ProcessDefinition definition = cache.get(processKey);
            
            if (definition != null) {
                meterRegistry.counter("pce_cache_hits", "type", "process_definition")
                    .increment();
                return Optional.of(definition);
            }
            
            meterRegistry.counter("pce_cache_misses", "type", "process_definition")
                .increment();
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error accessing cache for processKey: {}", processKey, e);
            meterRegistry.counter("pce_cache_errors", "type", "process_definition")
                .increment();
            // Fallback a base de datos
            return repository.findByProcessKey(processKey);
        }
    }
    
    public void invalidate(String processKey) {
        cache.invalidate(processKey);
        log.debug("Cache invalidated for processKey: {}", processKey);
    }
    
    public void invalidateAll() {
        cache.invalidateAll();
    }
    
    private ProcessDefinition loadFromDatabase(String processKey) {
        return repository.findByProcessKey(processKey)
            .orElse(null);
    }
    
    @Scheduled(fixedDelay = 60000)
    public void reportCacheStats() {
        CacheStats stats = cache.stats();
        log.info("Cache stats - Hits: {}, Misses: {}, Hit rate: {}%",
            stats.hitCount(),
            stats.missCount(),
            String.format("%.2f", stats.hitRate() * 100));
    }
}

// infrastructure/cache/ProcessRuleCache.java
@Component
@Slf4j
public class ProcessRuleCache {
    
    private final ProcessRuleQueries ruleQueries;
    private final Cache<String, List<ProcessRule>> cache;
    
    public ProcessRuleCache(ProcessRuleQueries ruleQueries) {
        this.ruleQueries = ruleQueries;
        
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500)
            .build(key -> loadRulesFromDatabase(key));
    }
    
    public List<ProcessRule> getActiveRules(String processKey) {
        return cache.get(processKey, key -> 
            ruleQueries.findActiveRulesByProcessKey(key));
    }
    
    public void invalidate(String processKey) {
        cache.invalidate(processKey);
    }
    
    private List<ProcessRule> loadRulesFromDatabase(String processKey) {
        return ruleQueries.findActiveRulesByProcessKey(processKey);
    }
}
```

### 7.2 Event Listener para Invalidación de Caché

```java
// infrastructure/cache/CacheInvalidationListener.java
@Component
@Slf4j
public class CacheInvalidationListener {
    
    private final ProcessDefinitionCache definitionCache;
    private final ProcessRuleCache ruleCache;
    
    @RabbitListener(queues = "config.sync.queue")
    public void onConfigurationSyncEvent(ConfigurationSyncEvent event) {
        log.info("Invalidating cache for process: {}", event.getProcessKey());
        definitionCache.invalidate(event.getProcessKey());
        ruleCache.invalidate(event.getProcessKey());
    }
}
```

---

## 8. Resiliencia y Manejo de Errores

### 8.1 Configuración de Resilience4j

```java
// config/ResilienceConfig.java
@Configuration
public class ResilienceConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // 50% de fallos activa CB
            .slowCallRateThreshold(50) // 50% de llamadas lentas activa CB
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }
    
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .intervalFunction(
                IntervalFunctions.exponentialBackoff(500, 2))
            .retryOnException(e -> 
                e instanceof DatabaseException ||
                e instanceof MessagingException)
            .build();
        
        return RetryRegistry.of(config);
    }
    
    @Bean
    public TimeoutRegistry timeoutRegistry() {
        TimeoutConfig config = TimeoutConfig.custom()
            .timeoutDuration(Duration.ofSeconds(5))
            .cancelRunningFuture(true)
            .build();
        
        return TimeoutRegistry.of(config);
    }
}
```

### 8.2 Excepciones Personalizadas

```java
// domain/exception/ProcessComplianceException.java
public abstract class ProcessComplianceException extends RuntimeException {
    public ProcessComplianceException(String message) {
        super(message);
    }
    
    public ProcessComplianceException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class ProcessNotConfiguredException extends ProcessComplianceException {
    public ProcessNotConfiguredException(String message) {
        super(message);
    }
}

public class RuleEvaluationException extends ProcessComplianceException {
    public RuleEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class InvalidEventContractException extends ProcessComplianceException {
    public InvalidEventContractException(String message) {
        super(message);
    }
}

public class UnsupportedRuleTypeException extends ProcessComplianceException {
    public UnsupportedRuleTypeException(String message) {
        super(message);
    }
}
```

---

## 9. Configuración de Plug Event Library

### 9.1 Modelo de Payload del Evento

```java
// application/event/ProcessCompletionEventPayload.java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcessCompletionEventPayload {
    
    private String processKey;
    private String executionId;
    private String status; // COMPLETED, FAILED, SKIPPED
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> metadata;
}
```

### 9.2 Consumidor de Eventos

```java
// infrastructure/messaging/ProcessEventConsumer.java
@Component
@Slf4j
public class ProcessEventConsumer {
    
    private final ProcessComplianceService complianceService;
    private final RabbitMQConsumer rabbitConsumer;
    private final MeterRegistry meterRegistry;
    
    @PostConstruct
    public void start() throws Exception {
        // Utilizar TypedEventHandler de plug-event-library para deserialización automática
        rabbitConsumer.connect();
        rabbitConsumer.startConsuming();
        
        log.info("ProcessEventConsumer started listening to process.event.completed");
    }
    
    /**
     * Maneja un evento de finalización de proceso.
     * El EventMessage llega deserializado automáticamente.
     * 
     * Estructura esperada del EventMessage:
     * - source: "asiento-cero-hub", "payment-processor", etc.
     * - type: "EXECUTE_CRONJOB", "BATCH_COMPLETED", etc.
     * - contextId: ID correlativo del flujo (opcional)
     * - payload: {
     *     "processKey": "asiento-cero-diario",
     *     "executionId": "exec-20260619-001",
     *     "status": "COMPLETED",
     *     "durationMs": 540000,
     *     "errorCode": null,
     *     "errorMessage": null,
     *     "metadata": {...}
     *   }
     */
    public void handleProcessEvent(EventMessage event) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Extraer datos del payload
            ProcessCompletionEventPayload payload = extractPayload(event);
            
            log.info("Processing event from source: {}, process: {}, execution: {}",
                event.getSource(),
                payload.getProcessKey(),
                payload.getExecutionId());
            
            // 2. Validar estructura del payload
            validatePayload(payload);
            
            // 3. Procesar evento
            complianceService.processCompletionEvent(
                payload,
                event.getEventId(),    // Usar eventId de plug-event para trazabilidad
                event.getSource(),     // Registrar origen
                event.getTimestamp()   // Registrar timestamp del evento
            );
            
            meterRegistry.counter(
                "pce_events_processed_total",
                "source", event.getSource()
            ).increment();
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Event processed in {}ms", duration);
            
        } catch (InvalidEventPayloadException e) {
            log.error("Invalid event payload: {}", e.getMessage());
            meterRegistry.counter("pce_invalid_events_total").increment();
            // No reintentamos, el mensaje se descarta
            
        } catch (Exception e) {
            log.error("Error processing event: {}", event.getEventId(), e);
            meterRegistry.counter("pce_processing_errors_total").increment();
            // Relanzar excepción para que RabbitMQ reintente
            throw new EventProcessingException("Failed to process event", e);
        }
    }
    
    /**
     * Extrae el payload del EventMessage.
     * El payload contiene Map<String, Object> con los datos específicos.
     */
    private ProcessCompletionEventPayload extractPayload(EventMessage event) {
        try {
            Map<String, Object> payload = event.getPayload();
            
            return ProcessCompletionEventPayload.builder()
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
            throw new InvalidEventPayloadException(
                "Failed to extract payload from event: " + e.getMessage(), e);
        }
    }
    
    private void validatePayload(ProcessCompletionEventPayload payload) {
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
        return LocalDateTime.parse(dateStr, 
            DateTimeFormatter.ISO_DATE_TIME);
    }
    
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
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
```

### 9.3 Configuración de Plug Event Library

```java
// infrastructure/messaging/PlugEventConsumerConfig.java
@Configuration
public class PlugEventConsumerConfig {
    
    /**
     * Configurar el RabbitMQConsumer de plug-event-library.
     * Se utiliza un bean factory para crear la instancia del consumidor.
     */
    @Bean
    public RabbitMQConsumer rabbitMQConsumer(
        @Value("${rabbitmq.host}") String host,
        @Value("${rabbitmq.port}") int port,
        @Value("${rabbitmq.username}") String username,
        @Value("${rabbitmq.password}") String password,
        @Value("${rabbitmq.consumer.queue:process.event.completed}") String queue,
        @Value("${rabbitmq.consumer.exchange:process.events}") String exchange,
        @Value("${rabbitmq.consumer.routing-key:process.completed.*}") String routingKey,
        @Value("${rabbitmq.consumer.prefetch:1}") int prefetch,
        ProcessEventConsumer processEventConsumer) {

        RabbitMQConnectionConfig connectionConfig = RabbitMQConnectionConfig.builder()
            .host(host)
            .port(port)
            .username(username)
            .password(password)
            .virtualHost("/")
            .automaticRecoveryEnabled(true)
            .build();

        RabbitMQConsumerConfig consumerConfig = RabbitMQConsumerConfig.builder()
            .queueName(queue)
            .exchange(exchange)
            .exchangeType("topic")
            .routingKey(routingKey)
            .autoAck(false)
            .prefetchCount(prefetch)
            .build();
        
        return RabbitMQConsumer.builder()
            .connectionConfig(connectionConfig)
            .consumerConfig(consumerConfig)
            .messageHandler(TypedEventHandler.forEventMessage(processEventConsumer::handleProcessEvent))
            .build();
    }
}
```

### 9.4 Ejemplo de Estructura del Evento Recibido

```json
{
  "eventId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
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
    "metadata": {
      "jobScheduleKey": "daily-balance",
      "region": "chile"
    }
  }
}
```

---

## 10. Metricas Prometheus

```java
// presentation/metrics/PrometheusMetricsRegistry.java
@Component
@Slf4j
public class PrometheusMetricsRegistry {
    
    private final MeterRegistry meterRegistry;
    
    @PostConstruct
    public void initializeMetrics() {
        // Contadores
        Counter.builder("pce_events_received_total")
            .description("Total de eventos recibidos desde RabbitMQ")
            .register(meterRegistry);
        
        Counter.builder("pce_events_evaluated_total")
            .description("Total de eventos evaluados")
            .tag("result", "ok") // o "fail"
            .register(meterRegistry);
        
        Counter.builder("pce_rule_violations_total")
            .description("Total de violaciones de reglas")
            .baseUnit("violations")
            .register(meterRegistry);
        
        Counter.builder("pce_critical_alerts_total")
            .description("Total de alertas críticas enviadas")
            .register(meterRegistry);
        
        // Timers
        Timer.builder("pce_evaluation_duration_seconds")
            .description("Tiempo de evaluación de reglas por evento")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        Timer.builder("pce_mysql_query_duration_seconds")
            .description("Tiempo de consulta a MySQL")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }
}
```

---

## 11. Consideraciones de Escalabilidad

### Para 100+ Procesos

**11.1 Bases de Datos**
- Índices en `(process_key, enabled)`, `(process_key, created_at)`.
- Particionamiento de `process_execution` por fecha (remoción automática de datos antiguos).
- Read replicas para consultas de reglas y ejecuciones previas.
- Connection pooling (HikariCP) con 20-50 conexiones.

**11.2 Caché en Memoria**
- Caffeine con TTL 5-10 minutos para definiciones y reglas activas.
- Hit rates esperados: >80% para procesos activos.

**11.3 Consumo de RabbitMQ**
- Múltiples instancias del motor escuchando la misma queue (auto-scaling).
- Prefetch = 1 para asegurar distribución equitativa.
- Reenqueue automático en Dead Letter Queue para reintentos.

**11.4 Paralelismo**
- ThreadPoolExecutor con 20-50 threads para evaluación concurrente.
- Virtual threads (Project Loom) disponibles y recomendados en Java 25.

**11.5 Monitoreo**
- Métricas Prometheus scrapeadas cada 30 segundos.
- Alertas en Grafana para:
  - Latencia P95 > 1 segundo.
  - Tasa de fallos > 1%.
  - Circuit breaker abierto.

---

## 12. Ejemplo de Configuración application.yml

```yaml
spring:
  application:
    name: process-compliance-engine
  
  datasource:
    url: jdbc:mysql://localhost:3306/process_engine?useSSL=false&serverTimezone=UTC
    username: root
    password: ${DB_PASSWORD:secret}
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5
      connection-timeout: 10000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        jdbc:
          batch_size: 20
        order_inserts: true
        cache:
          use_second_level_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory

# Configuración de plug-event-library
rabbitmq:
  host: ${RABBITMQ_HOST:localhost}
  port: ${RABBITMQ_PORT:5672}
  username: ${RABBITMQ_USERNAME:guest}
  password: ${RABBITMQ_PASSWORD:guest}
  
  # Configuración de consumer
  consumer:
    queue: process.event.completed
    prefetch: 1
    concurrency: 10
    max-concurrency: 50
    auto-ack: false  # Manual acknowledgment para garantizar procesamiento

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

resilience4j:
  circuitbreaker:
    instances:
      processCompliance:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  retry:
    instances:
      processCompliance:
        max-attempts: 3
        wait-duration: 500ms
  timeout:
    instances:
      processCompliance:
        timeout-duration: 5s
```

---

## 13. Testing

```java
// src/test/java/com/flexibilitytech/pce/application/
// ProcessComplianceServiceTest.java

@SpringBootTest
public class ProcessComplianceServiceTest {
    
    @MockBean
    private ProcessDefinitionRepository processDefinitionRepo;
    
    @MockBean
    private RuleEngine ruleEngine;
    
    @Autowired
    private ProcessComplianceService service;
    
    @Test
    public void testProcessCompletionEventSuccess() {
        // Arrange
        ProcessCompletionEventPayload payload = ProcessCompletionEventPayload.builder()
            .processKey("test-process")
            .executionId("exec-001")
            .status("COMPLETED")
            .durationMs(120000L)
            .build();
        
        ProcessDefinition definition = new ProcessDefinition();
        definition.setProcessKey("test-process");
        
        when(processDefinitionRepo.findByProcessKey("test-process"))
            .thenReturn(Optional.of(definition));
        
        // Act
        service.processCompletionEvent(payload, "event-001", "test-source", "2026-06-19T08:00:00Z");
        
        // Assert
        verify(ruleEngine, times(1)).evaluateProcess(any(), any());
    }
    
    @Test
    public void testProcessCompletionEventProcessNotFound() {
        // Arrange
        ProcessCompletionEventPayload payload = ProcessCompletionEventPayload.builder()
            .processKey("unknown-process")
            .executionId("exec-001")
            .build();
        
        when(processDefinitionRepo.findByProcessKey("unknown-process"))
            .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(ProcessNotConfiguredException.class,
            () -> service.processCompletionEvent(payload, "event-001", "test-source", "2026-06-19T08:00:00Z"));
    }
}
```

---

## 14. Depedencias Maven

```xml
<!-- pom.xml -->
<properties>
    <java.version>25</java.version>
    <maven.compiler.release>25</maven.compiler.release>
    <spring-boot.version>4.0.0</spring-boot.version>
</properties>

<dependency>
    <groupId>com.flexibility</groupId>
    <artifactId>plug-consumer-rabbitmq</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>com.flexibility</groupId>
    <artifactId>plug-events-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot y dependencias relacionadas -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>${spring-boot.version}</version>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
    <version>${spring-boot.version}</version>
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.12.0</version>
</dependency>

<!-- Resilience -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot4</artifactId>
    <version>2.3.0</version>
</dependency>

<!-- Caching -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>

<!-- MySQL Driver -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.0.33</version>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
</dependency>
```

---

## 15. Ejemplo: Cómo Enviar Eventos desde un Productor

### Usando plug-producer-rabbitmq

```java
// Ejemplo en el servicio asiento-cero-hub que publica eventos

@Component
public class ProcessEventProducer {
    
    private final RabbitMQProducer producer;
    
    public void publishProcessCompletion(
        String processKey,
        String executionId,
        String status,
        long durationMs,
        String errorCode,
        String errorMessage,
        Map<String, Object> metadata) {
        
        // 1. Construir payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("processKey", processKey);
        payload.put("executionId", executionId);
        payload.put("status", status);
        payload.put("scheduledAt", LocalDateTime.now().minusSeconds(durationMs / 1000).toString());
        payload.put("startedAt", LocalDateTime.now().minusSeconds(durationMs / 1000).toString());
        payload.put("endedAt", LocalDateTime.now().toString());
        payload.put("durationMs", durationMs);
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);
        payload.put("metadata", metadata);
        
        // 2. Crear EventMessage usando plug-event-library
        EventMessage event = EventMessage.create(
            "asiento-cero-hub",        // source
            "EXECUTE_CRONJOB",         // type
            processKey + "-job",       // contextId
            payload                    // payload
        );
        
        // 3. Publicar evento
        producer.publishEvent(event);
    }
}

// Uso en el servicio después de completar un proceso
try {
    // ... ejecutar lógica del proceso ...
    
    // Publicar éxito
    processEventProducer.publishProcessCompletion(
        "asiento-cero-diario",
        "exec-20260619-001",
        "COMPLETED",
        789000L,
        null,
        null,
        Map.of("jobScheduleKey", "daily-balance", "region", "chile")
    );
    
} catch (Exception e) {
    // Publicar fallo
    processEventProducer.publishProcessCompletion(
        "asiento-cero-diario",
        "exec-20260619-001",
        "FAILED",
        127000L,
        "E101",
        "Connection timeout to data source",
        Map.of("jobScheduleKey", "daily-balance")
    );
}
```

---

## 16. Consideraciones Finales sobre plug-event-library

### Ventajas de usar la librería

- **Estándar corporativo**: Todos los eventos siguen el mismo formato
- **Deserialización automática**: TypedEventHandler maneja JSON automáticamente
- **Trazabilidad**: eventId y timestamp centralizados
- **Idempotencia**: eventId garantiza procesamiento único
- **Correlación**: contextId permite correlacionar eventos relacionados
- **Flexibilidad**: payload Map<String, Object> adaptable a nuevos campos

### Beneficios para escalabilidad

- **Contrato único**: Independiente de cambios en sistemas productores
- **Versionado implícito**: Nuevos campos en payload no rompen deserialization
- **Monitoreo unificado**: Métricas por source, type y contextId
- **Resiliencia**: La estructura estándar facilita reintentos y DLQ

### Patrón de Error Handling

```java
// Dead Letter Queue - Reintentos automáticos
// Si ProcessEventConsumer.handleProcessEvent() lanza excepción:
// 1. RabbitMQ reintenta 3 veces (configurable en Resilience4j)
// 2. Si sigue fallando, el mensaje va a DLQ
// 3. Servicio de monitoring detecta DLQ y alerta

// Manual acknowledgment (autoAck=false en RabbitMQConsumerConfig)
// garantiza que si el procesamiento falla, el evento se reintenta
```

---

## 17. Conclusiones

- **Arquitectura modular**: Factory pattern para evaluadores, inyección de dependencias.
- **Escalabilidad**: Caché en memoria, índices DB, múltiples instancias.
- **Robustez**: Circuit breakers, reintentos, timeouts, manejo de excepciones.
- **Performance**: Queries optimizadas, batch processing, async donde aplica.
- **Observabilidad**: Métricas Prometheus integrales, logging estructurado.
- **Integración estándar**: Uso de plug-event-library para eventos corporativos.
- **Trazabilidad**: eventId, timestamp y source centralizados en todos los eventos.

La arquitectura soporta 100+ procesos con latencia <1 segundo por evento, mediante:

1. **Consumidor basado en plug-event-library**: Deserialización automática, manejo estándar
2. **Procesamiento resiliente**: Circuit breaker + Retry + Timeout
3. **Deduplicación garantizada**: Unique index en BD + verificación en memoria
4. **Caché de 2 capas**: Definiciones (TTL 5min) + Reglas (TTL 10min) con Caffeine
5. **Queries optimizadas**: Índices estratégicos, proyecciones, paginación cuando aplica
6. **Métricas granulares**: Por source, rule_type, severity para visibilidad total
