package com.example.orderprocessor.integration;

import com.example.orderprocessor.model.Order;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.example.orderprocessor.model.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class OrderProcessingIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("orderdb_test")
            .withUsername("user")
            .withPassword("password");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void testOrderProcessingFlow() throws InterruptedException {
        // Publish event
        OrderPlacedEvent event = new OrderPlacedEvent("evt123", "prodX", 2, "cust1", "2023-10-27T10:00:00Z");
        rabbitTemplate.convertAndSend("order.events", "order.placed", event);

        // Poll for up to 10 seconds
        boolean processed = false;
        for (int i = 0; i < 20; i++) {
            Optional<Order> order = orderRepository.findById("evt123");
            if (order.isPresent() && order.get().getStatus() == OrderStatus.PROCESSED) {
                processed = true;
                break;
            }
            Thread.sleep(500);
        }

        assertTrue(processed, "Order should be processed within 10 seconds");
    }
}
