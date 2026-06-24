package com.flexibility.pce.infrastructure.persistence.query;

import com.flexibility.pce.domain.entity.ProcessExecution;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProcessExecutionQueries {

    private final EntityManager entityManager;

    public Optional<ProcessExecution> findLastExecutionByProcessKey(String processKey) {
        List<ProcessExecution> result = entityManager.createQuery(
            "SELECT e FROM ProcessExecution e WHERE e.processKey = :processKey " +
            "AND e.status IN ('COMPLETED', 'FAILED') ORDER BY e.endedAt DESC",
            ProcessExecution.class)
            .setParameter("processKey", processKey)
            .setMaxResults(1)
            .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public boolean existsExecutionId(String processKey, String executionId) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(e) FROM ProcessExecution e WHERE e.processKey = :processKey AND e.executionId = :executionId",
            Long.class)
            .setParameter("processKey", processKey)
            .setParameter("executionId", executionId)
            .getSingleResult();
        return count > 0;
    }

    public long countByProcessKeyAndExecutionId(String processKey, String executionId) {
        return entityManager.createQuery(
            "SELECT COUNT(e) FROM ProcessExecution e WHERE e.processKey = :processKey AND e.executionId = :executionId",
            Long.class)
            .setParameter("processKey", processKey)
            .setParameter("executionId", executionId)
            .getSingleResult();
    }
}
