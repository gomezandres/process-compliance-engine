package com.flexibility.pce.application.service;

import com.flexibility.pce.application.dto.AlertDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AlertService {

    public void sendAlert(AlertDTO alert) {
        log.warn("ALERT [{}] process={} rule={}: {}",
            alert.getSeverity(), alert.getProcessKey(), alert.getRuleType(), alert.getMessage());
    }

    public void raiseConfigurationAlert(String processKey, String message) {
        log.error("CONFIGURATION ALERT process={}: {}", processKey, message);
    }
}
