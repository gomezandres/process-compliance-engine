package com.flexibility.pce.domain.exception;

public class InvalidEventPayloadException extends ProcessComplianceException {
    public InvalidEventPayloadException(String message) { super(message); }
    public InvalidEventPayloadException(String message, Throwable cause) { super(message, cause); }
}
