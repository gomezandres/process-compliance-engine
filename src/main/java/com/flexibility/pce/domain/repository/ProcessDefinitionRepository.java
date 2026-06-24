package com.flexibility.pce.domain.repository;

import com.flexibility.pce.domain.entity.ProcessDefinition;
import java.util.List;
import java.util.Optional;

public interface ProcessDefinitionRepository {
    Optional<ProcessDefinition> findByProcessKey(String processKey);
    List<ProcessDefinition> findAllActive();
    ProcessDefinition save(ProcessDefinition definition);
}
