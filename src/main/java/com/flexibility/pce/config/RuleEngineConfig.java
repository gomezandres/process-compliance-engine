package com.flexibility.pce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexibility.pce.domain.service.RuleEngine;
import com.flexibility.pce.domain.service.RuleEvaluator;
import com.flexibility.pce.domain.service.RuleEvaluatorFactory;
import com.flexibility.pce.domain.service.evaluators.*;
import com.flexibility.pce.infrastructure.persistence.query.ProcessExecutionQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class RuleEngineConfig {

    @Bean
    public RuleEvaluatorFactory ruleEvaluatorFactory(ProcessExecutionQueries executionQueries,
            ObjectMapper objectMapper) {
        Map<String, RuleEvaluator> evaluators = Map.of(
            "SLA_MAX_MINUTES",      new SlaMaxMinutesEvaluator(),
            "FAIL_STATUS",          new FailStatusEvaluator(),
            "DUPLICATE_EXECUTION",  new DuplicateExecutionEvaluator(executionQueries),
            "ALLOWED_ERROR_CODES",  new AllowedErrorCodesEvaluator(objectMapper),
            "MAX_DURATION_MS",      new MaxDurationMsEvaluator(),
            "MISSED_SCHEDULE",      new MissedScheduleEvaluator()
        );
        return new RuleEvaluatorFactory(evaluators);
    }

    @Bean
    public RuleEngine ruleEngine(RuleEvaluatorFactory factory) {
        return new RuleEngine(factory);
    }
}
