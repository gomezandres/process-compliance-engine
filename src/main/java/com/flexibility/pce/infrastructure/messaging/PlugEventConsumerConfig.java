package com.flexibility.pce.infrastructure.messaging;

import com.flexibility.plug.consumer.rabbitmq.RabbitMQConnectionConfig;
import com.flexibility.plug.consumer.rabbitmq.RabbitMQConsumer;
import com.flexibility.plug.consumer.rabbitmq.RabbitMQConsumerConfig;
import com.flexibility.plug.events.common.TypedEventHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlugEventConsumerConfig {

    @Bean
    public RabbitMQConsumer rabbitMQConsumer(
            @Value("${rabbitmq.host}") String host,
            @Value("${rabbitmq.port}") int port,
            @Value("${rabbitmq.username}") String username,
            @Value("${rabbitmq.password}") String password,
            @Value("${rabbitmq.consumer.queue:process.event.lifecycle}") String queue,
            @Value("${rabbitmq.consumer.exchange:process.events}") String exchange,
            @Value("${rabbitmq.consumer.routing-key:process.lifecycle.*}") String routingKey,
            @Value("${rabbitmq.consumer.prefetch:1}") int prefetch,
            ProcessEventConsumer processEventConsumer) {

        RabbitMQConnectionConfig connectionConfig = RabbitMQConnectionConfig.builder()
            .host(host).port(port).username(username).password(password)
            .virtualHost("/").automaticRecoveryEnabled(true).build();

        RabbitMQConsumerConfig consumerConfig = RabbitMQConsumerConfig.builder()
            .queueName(queue).exchange(exchange).exchangeType("topic")
            .routingKey(routingKey).autoAck(false).prefetchCount(prefetch).build();

        return RabbitMQConsumer.builder()
            .connectionConfig(connectionConfig)
            .consumerConfig(consumerConfig)
            .messageHandler(TypedEventHandler.forEventMessage(processEventConsumer::handleProcessEvent))
            .build();
    }
}
