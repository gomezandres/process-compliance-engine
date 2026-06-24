---
description: Checklist and constraints for adding or modifying rule evaluators
globs: ["src/main/java/**/domain/service/**/*.java", "src/main/java/**/application/service/**/*.java"]
alwaysApply: false
---

# Evaluator Pattern

## Adding a new rule type — mandatory checklist

Every new `rule_type` requires exactly these three steps. No more, no less.

1. **New evaluator class** in `domain/service/evaluators/`
   - Implements `RuleEvaluator`
   - Annotated `@Component`
   - Single public method: `evaluate(ProcessRule rule, ProcessExecution execution)`

2. **Register in factory** — one line in `RuleEvaluatorFactory`:
   ```java
   Map.entry("YOUR_RULE_TYPE", yourEvaluator)
   ```
   The key must match the `rule_type` value stored in `process_rule.rule_type` exactly.

3. **Unit test** in `src/test/java/.../domain/service/evaluators/`
   - Test violated = true case
   - Test violated = false case
   - Test null/missing input case (should return violated=false, not throw)

## Error handling inside the evaluation loop — invariant

The `RuleEngine` evaluation loop **must never abort** due to a single rule failure.

```java
// CORRECT — catch per rule, continue loop
for (ProcessRule rule : activeRules) {
    try {
        RuleEvaluator evaluator = evaluatorFactory.getEvaluator(rule.getRuleType());
        RuleEvaluationResult result = evaluator.evaluate(rule, execution);
        // ... collect violations
    } catch (Exception e) {
        log.error("Error evaluating rule: {}", rule.getId(), e);
        meterRegistry.counter("pce_rule_evaluation_error",
            "rule_type", rule.getRuleType()).increment();
        // continue — do NOT rethrow here
    }
}

// WRONG — letting an exception escape the loop
for (ProcessRule rule : activeRules) {
    RuleEvaluationResult result = evaluator.evaluate(rule, execution); // throws → aborts all rules
}
```

## Evaluator contract

- Return `violated = false` (not throw) when required input is null or absent.
- Never call the database directly. If an evaluator needs historical data, it must receive it via `ProcessExecution` or a dedicated query object injected at construction.
- Do not put orchestration logic (metrics recording, alert sending) inside an evaluator — that belongs in `RuleEngine` or `ProcessComplianceService`.

## Current rule types

`SLA_MAX_MINUTES` · `FAIL_STATUS` · `DUPLICATE_EXECUTION` · `ALLOWED_ERROR_CODES` · `MAX_DURATION_MS` · `MISSED_SCHEDULE`
