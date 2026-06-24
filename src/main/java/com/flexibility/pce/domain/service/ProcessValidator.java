package com.flexibility.pce.domain.service;

import com.flexibility.pce.domain.entity.ProcessDefinition;
import com.flexibility.pce.domain.exception.InvalidEventContractException;

public class ProcessValidator {

    public void validateRequiredPayloadFields(String processKey, String executionId, String status) {
        if (processKey == null || processKey.isBlank()) {
            throw new InvalidEventContractException("processKey is required");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new InvalidEventContractException("executionId is required");
        }
        if (status == null || status.isBlank()) {
            throw new InvalidEventContractException("status is required");
        }
    }

    public void validateDefinitionIsActive(ProcessDefinition definition) {
        if (!definition.isActive()) {
            throw new InvalidEventContractException("Process is not active: " + definition.getProcessKey());
        }
    }
}
