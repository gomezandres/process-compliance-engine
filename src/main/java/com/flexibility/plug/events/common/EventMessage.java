package com.flexibility.plug.events.common;

import java.util.Map;

public class EventMessage {
    private String eventId;
    private String timestamp;
    private String source;
    private String type;
    private String contextId;
    private Map<String, Object> payload;

    public String getEventId()  { return eventId; }
    public String getTimestamp(){ return timestamp; }
    public String getSource()   { return source; }
    public String getType()     { return type; }
    public String getContextId(){ return contextId; }
    public Map<String, Object> getPayload() { return payload; }

    public void setEventId(String eventId)   { this.eventId = eventId; }
    public void setTimestamp(String ts)      { this.timestamp = ts; }
    public void setSource(String source)     { this.source = source; }
    public void setType(String type)         { this.type = type; }
    public void setContextId(String ctx)     { this.contextId = ctx; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
