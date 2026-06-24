package com.flexibility.pce.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "process_rule", indexes = {
    @Index(name = "idx_rule_process_enabled", columnList = "process_key, enabled")
})
@Data @AllArgsConstructor @NoArgsConstructor
public class ProcessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_key", nullable = false)
    private String processKey;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleOperator operator;

    @Column(name = "value_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ValueType valueType;

    @Column(name = "threshold_number")
    private BigDecimal thresholdNumber;

    @Column(name = "threshold_string")
    private String thresholdString;

    @Column(name = "threshold_boolean")
    private Boolean thresholdBoolean;

    @Column(name = "threshold_json", columnDefinition = "JSON")
    private String thresholdJson;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(nullable = false)
    private Boolean enabled;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
