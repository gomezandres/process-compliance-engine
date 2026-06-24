package com.flexibility.pce.presentation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrometheusMetricsRegistry {

    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void initializeMetrics() {
        Counter.builder("pce_events_received_total")
            .description("Total events received from RabbitMQ").register(meterRegistry);
        Counter.builder("pce_events_evaluated_total")
            .description("Total completion events evaluated").tag("result", "PASS").register(meterRegistry);
        Counter.builder("pce_rule_violations_total")
            .description("Total rule violations").register(meterRegistry);
        Counter.builder("pce_critical_alerts_total")
            .description("Total critical alerts sent").register(meterRegistry);
        Counter.builder("pce_events_unknown_process_total")
            .description("Events for processes without configuration").register(meterRegistry);
        Counter.builder("pce_executions_hung_total")
            .description("Executions started but never completed within grace window").register(meterRegistry);
        Timer.builder("pce_evaluation_duration_seconds")
            .description("Rule evaluation duration per event")
            .publishPercentiles(0.5, 0.95, 0.99).register(meterRegistry);
        Timer.builder("pce_mysql_query_duration_seconds")
            .description("MySQL query duration")
            .publishPercentiles(0.5, 0.95, 0.99).register(meterRegistry);
    }
}
