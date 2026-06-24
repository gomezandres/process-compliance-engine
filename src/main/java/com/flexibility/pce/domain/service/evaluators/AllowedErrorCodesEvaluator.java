package com.flexibility.pce.domain.service.evaluators;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.service.RuleEvaluationResult;
import com.flexibility.pce.domain.service.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AllowedErrorCodesEvaluator implements RuleEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public RuleEvaluationResult evaluate(ProcessRule rule, ProcessExecution execution) {
        String errorCode = execution.getErrorCode();
        if (errorCode == null) {
            return new RuleEvaluationResult(false, "No error code", null, null);
        }
        List<String> allowedCodes = parseJsonArray(rule.getThresholdJson());
        boolean isAllowed = allowedCodes.contains(errorCode);
        return new RuleEvaluationResult(!isAllowed,
            String.format("Error code %s not in allowed list", errorCode),
            errorCode, allowedCodes);
    }

    private List<String> parseJsonArray(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse threshold JSON: {}", json, e);
            return Collections.emptyList();
        }
    }
}
