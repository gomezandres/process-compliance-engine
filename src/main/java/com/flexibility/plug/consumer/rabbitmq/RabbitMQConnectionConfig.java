package com.flexibility.plug.consumer.rabbitmq;

public class RabbitMQConnectionConfig {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String virtualHost;
    private final boolean automaticRecoveryEnabled;

    private RabbitMQConnectionConfig(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.username = b.username;
        this.password = b.password;
        this.virtualHost = b.virtualHost;
        this.automaticRecoveryEnabled = b.automaticRecoveryEnabled;
    }

    public static Builder builder() { return new Builder(); }

    public String getHost()    { return host; }
    public int getPort()       { return port; }
    public String getUsername(){ return username; }
    public String getPassword(){ return password; }
    public String getVirtualHost() { return virtualHost; }
    public boolean isAutomaticRecoveryEnabled() { return automaticRecoveryEnabled; }

    public static class Builder {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        private boolean automaticRecoveryEnabled = true;

        public Builder host(String h)               { this.host = h; return this; }
        public Builder port(int p)                  { this.port = p; return this; }
        public Builder username(String u)           { this.username = u; return this; }
        public Builder password(String p)           { this.password = p; return this; }
        public Builder virtualHost(String v)        { this.virtualHost = v; return this; }
        public Builder automaticRecoveryEnabled(boolean e) { this.automaticRecoveryEnabled = e; return this; }
        public RabbitMQConnectionConfig build()     { return new RabbitMQConnectionConfig(this); }
    }
}
