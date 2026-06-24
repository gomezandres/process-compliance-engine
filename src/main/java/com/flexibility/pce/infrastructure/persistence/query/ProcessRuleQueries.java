package com.flexibility.pce.infrastructure.persistence.query;

import com.flexibility.pce.domain.entity.ProcessRule;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProcessRuleQueries {

    private final EntityManager entityManager;

    public List<ProcessRule> findActiveRulesByProcessKey(String processKey) {
        return entityManager.createQuery(
            "SELECT r FROM ProcessRule r WHERE r.processKey = :processKey AND r.enabled = true ORDER BY r.id ASC",
            ProcessRule.class)
            .setParameter("processKey", processKey)
            .getResultList();
    }
}
