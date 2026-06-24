package com.flexibility.pce.domain.repository;

import com.flexibility.pce.domain.entity.ProcessRule;
import java.util.List;

public interface ProcessRuleRepository {
    List<ProcessRule> findActiveByProcessKey(String processKey);
}
