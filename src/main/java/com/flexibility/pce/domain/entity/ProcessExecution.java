package com.flexibility.pce.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "process_execution", indexes = {
    @Index(name = "idx_execution_process", columnList = "process_key"),
    @Index(name = "idx_execution_id", columnList = "execution_id"),
    @Index(name = "idx_execution_created", columnList = "process_key, created_at"),
    @Index(name = "idx_execution_running", columnList = "process_key, status, started_at")
})
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ProcessExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private String executionId;

    @Column(name = "process_key", nullable = false)
    private String processKey;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
