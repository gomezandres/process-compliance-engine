package com.flexibility.plug.consumer.rabbitmq;

public class RabbitMQConsumerConfig {
    private final String queueName;
    private final String exchange;
    private final String exchangeType;
    private final String routingKey;
    private final boolean autoAck;
    private final int prefetchCount;

    private RabbitMQConsumerConfig(Builder b) {
        this.queueName    = b.queueName;
        this.exchange     = b.exchange;
        this.exchangeType = b.exchangeType;
        this.routingKey   = b.routingKey;
        this.autoAck      = b.autoAck;
        this.prefetchCount = b.prefetchCount;
    }

    public static Builder builder() { return new Builder(); }

    public String getQueueName()   { return queueName; }
    public String getExchange()    { return exchange; }
    public String getExchangeType(){ return exchangeType; }
    public String getRoutingKey()  { return routingKey; }
    public boolean isAutoAck()     { return autoAck; }
    public int getPrefetchCount()  { return prefetchCount; }

    public static class Builder {
        private String queueName;
        private String exchange;
        private String exchangeType = "topic";
        private String routingKey;
        private boolean autoAck = false;
        private int prefetchCount = 1;

        public Builder queueName(String q)    { this.queueName = q; return this; }
        public Builder exchange(String e)     { this.exchange = e; return this; }
        public Builder exchangeType(String t) { this.exchangeType = t; return this; }
        public Builder routingKey(String r)   { this.routingKey = r; return this; }
        public Builder autoAck(boolean a)     { this.autoAck = a; return this; }
        public Builder prefetchCount(int p)   { this.prefetchCount = p; return this; }
        public RabbitMQConsumerConfig build() { return new RabbitMQConsumerConfig(this); }
    }
}
