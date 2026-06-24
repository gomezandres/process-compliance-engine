package com.flexibility.plug.consumer.rabbitmq;

import com.flexibility.plug.events.common.TypedEventHandler;

public class RabbitMQConsumer {

    private final RabbitMQConnectionConfig connectionConfig;
    private final RabbitMQConsumerConfig consumerConfig;
    private final TypedEventHandler messageHandler;

    private RabbitMQConsumer(Builder b) {
        this.connectionConfig = b.connectionConfig;
        this.consumerConfig   = b.consumerConfig;
        this.messageHandler   = b.messageHandler;
    }

    public static Builder builder() { return new Builder(); }

    public void connect() throws Exception {
        throw new UnsupportedOperationException("plug-consumer-rabbitmq stub — replace with real library");
    }

    public void startConsuming() throws Exception {
        throw new UnsupportedOperationException("plug-consumer-rabbitmq stub — replace with real library");
    }

    public void stopConsuming() throws Exception {
        // no-op in stub
    }

    public void close() throws Exception {
        // no-op in stub
    }

    public static class Builder {
        private RabbitMQConnectionConfig connectionConfig;
        private RabbitMQConsumerConfig consumerConfig;
        private TypedEventHandler messageHandler;

        public Builder connectionConfig(RabbitMQConnectionConfig c) { this.connectionConfig = c; return this; }
        public Builder consumerConfig(RabbitMQConsumerConfig c)     { this.consumerConfig = c; return this; }
        public Builder messageHandler(TypedEventHandler h)          { this.messageHandler = h; return this; }
        public RabbitMQConsumer build()                             { return new RabbitMQConsumer(this); }
    }
}
