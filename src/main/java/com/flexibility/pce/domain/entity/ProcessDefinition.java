package com.flexibility.pce.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "process_definition")
@Data @AllArgsConstructor @NoArgsConstructor
public class ProcessDefinition {

    @Id
    @Column(name = "process_key")
    private String processKey;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "schedule_cron", nullable = false)
    private String scheduleCron;

    @Column(name = "schedule_timezone", nullable = false)
    private String scheduleTimezone;

    @Column(name = "schedule_grace_minutes", nullable = false)
    private Integer scheduleGraceMinutes;

    @Column
    private String owner;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "processKey", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ProcessRule> rules = new HashSet<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
