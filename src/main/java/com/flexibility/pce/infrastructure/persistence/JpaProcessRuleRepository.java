package com.flexibility.pce.infrastructure.persistence;

import com.flexibility.pce.domain.entity.ProcessRule;
import com.flexibility.pce.domain.repository.ProcessRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaProcessRuleRepository implements ProcessRuleRepository {

    private final ProcessRuleSpringDataRepository jpaRepository;

    @Override
    public List<ProcessRule> findActiveByProcessKey(String processKey) {
        return jpaRepository.findActiveByProcessKey(processKey);
    }
}

@Repository
interface ProcessRuleSpringDataRepository extends JpaRepository<ProcessRule, Long> {
    @Query("SELECT r FROM ProcessRule r WHERE r.processKey = :processKey AND r.enabled = true ORDER BY r.id ASC")
    List<ProcessRule> findActiveByProcessKey(@Param("processKey") String processKey);
}
