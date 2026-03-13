package com.example.orderprocessor.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.order.exchange}")
    private String exchange;

    @Value("${rabbitmq.order.placed-queue}")
    private String placedQueue;

    @Value("${rabbitmq.order.placed-routing-key}")
    private String placedRoutingKey;

    @Value("${rabbitmq.order.dlx}")
    private String dlx;

    @Value("${rabbitmq.order.dlq}")
    private String dlq;

    // Exchange
    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(exchange);
    }

    // Dead Letter Exchange
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(dlx);
    }

    // Queue with DLQ config
    @Bean
    public Queue orderPlacedQueue() {
        return QueueBuilder.durable(placedQueue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", placedRoutingKey)
                .build();
    }

    // Dead Letter Queue
    @Bean
    public Queue orderDlq() {
        return QueueBuilder.durable(dlq).build();
    }

    // Bindings
    @Bean
    public Binding bindOrderPlacedQueue(Queue orderPlacedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderPlacedQueue).to(orderEventsExchange).with(placedRoutingKey);
    }

    @Bean
    public Binding bindOrderDlq(Queue orderDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(orderDlq).to(deadLetterExchange).with(placedRoutingKey);
    }

    // Message Converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
