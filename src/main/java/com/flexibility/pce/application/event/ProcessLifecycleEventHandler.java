package com.flexibility.pce.application.event;

public interface ProcessLifecycleEventHandler {
    void handleStartEvent(ProcessLifecycleEventPayload payload, String eventId, String source, String timestamp);
    void handleCompletionEvent(ProcessLifecycleEventPayload payload, String eventId, String source, String timestamp);
}
