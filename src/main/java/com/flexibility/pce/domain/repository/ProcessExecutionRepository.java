package com.flexibility.pce.domain.repository;

import com.flexibility.pce.domain.entity.ProcessExecution;
import java.util.Optional;

public interface ProcessExecutionRepository {
    ProcessExecution save(ProcessExecution execution);
    Optional<ProcessExecution> findByProcessKeyAndExecutionId(String processKey, String executionId);
}
