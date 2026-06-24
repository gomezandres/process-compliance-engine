package com.flexibility.pce.domain.exception;

public abstract class ProcessComplianceException extends RuntimeException {
    public ProcessComplianceException(String message) { super(message); }
    public ProcessComplianceException(String message, Throwable cause) { super(message, cause); }
}
