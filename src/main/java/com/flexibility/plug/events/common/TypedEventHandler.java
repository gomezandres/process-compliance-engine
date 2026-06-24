package com.flexibility.plug.events.common;

import java.util.function.Consumer;

public class TypedEventHandler {

    private final Consumer<EventMessage> handler;

    private TypedEventHandler(Consumer<EventMessage> handler) {
        this.handler = handler;
    }

    public static TypedEventHandler forEventMessage(Consumer<EventMessage> handler) {
        return new TypedEventHandler(handler);
    }

    public void handle(EventMessage message) {
        handler.accept(message);
    }
}
