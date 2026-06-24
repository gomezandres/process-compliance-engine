package com.flexibility.pce.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.function.Supplier;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseCircuitBreaker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public <T> T execute(String name, Supplier<T> supplier) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        return cb.executeSupplier(supplier);
    }
}
