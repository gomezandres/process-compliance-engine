package com.flexibility.pce.infrastructure.persistence;

import com.flexibility.pce.domain.entity.ProcessDefinition;
import com.flexibility.pce.domain.repository.ProcessDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaProcessDefinitionRepository implements ProcessDefinitionRepository {

    private final ProcessDefinitionSpringDataRepository jpaRepository;

    @Override
    public Optional<ProcessDefinition> findByProcessKey(String processKey) {
        return jpaRepository.findById(processKey);
    }

    @Override
    public List<ProcessDefinition> findAllActive() {
        return jpaRepository.findAllByActiveTrue();
    }

    @Override
    public ProcessDefinition save(ProcessDefinition definition) {
        return jpaRepository.save(definition);
    }
}

@Repository
interface ProcessDefinitionSpringDataRepository extends JpaRepository<ProcessDefinition, String> {
    @Query("SELECT p FROM ProcessDefinition p WHERE p.active = true")
    List<ProcessDefinition> findAllByActiveTrue();
}
