package com.flexibility.pce.infrastructure.persistence;

import com.flexibility.pce.domain.entity.ProcessExecution;
import com.flexibility.pce.domain.repository.ProcessExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaProcessExecutionRepository implements ProcessExecutionRepository {

    private final ProcessExecutionSpringDataRepository jpaRepository;

    @Override
    public ProcessExecution save(ProcessExecution execution) {
        return jpaRepository.save(execution);
    }

    @Override
    public Optional<ProcessExecution> findByProcessKeyAndExecutionId(String processKey, String executionId) {
        return jpaRepository.findByProcessKeyAndExecutionId(processKey, executionId);
    }
}

@Repository
interface ProcessExecutionSpringDataRepository extends JpaRepository<ProcessExecution, Long> {
    Optional<ProcessExecution> findByProcessKeyAndExecutionId(String processKey, String executionId);
}
