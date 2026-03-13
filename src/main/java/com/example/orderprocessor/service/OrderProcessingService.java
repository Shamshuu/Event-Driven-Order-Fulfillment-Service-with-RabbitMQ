package com.example.orderprocessor.service;

import com.example.orderprocessor.model.Order;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.example.orderprocessor.model.OrderProcessedEvent;
import com.example.orderprocessor.model.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.order.exchange}")
    private String exchange;

    @RabbitListener(queues = "${rabbitmq.order.placed-queue}")
    @Transactional
    public void processOrderPlacedEvent(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent: {}", event);

        // 1. Validation (Permanent Error check)
        if (event == null || event.getOrderId() == null || event.getProductId() == null) {
            log.error("Invalid event data. Moving to DLQ permanently. Event: {}", event);
            throw new AmqpRejectAndDontRequeueException("Invalid event data: missing orderId or productId");
        }

        try {
            // 2. Idempotency Check
            Order order = orderRepository.findById(event.getOrderId()).orElse(null);

            if (order != null) {
                if (order.getStatus() == OrderStatus.PROCESSED || order.getStatus() == OrderStatus.PROCESSING) {
                    log.info("Order {} is already in {} state. Ignoring duplicate event.", order.getId(), order.getStatus());
                    return; // Already processed/processing, ACK message automatically
                }
            } else {
                // Create new order if it doesn't exist logically
                order = new Order();
                order.setId(event.getOrderId());
                order.setProductId(event.getProductId());
                order.setCustomerId(event.getCustomerId());
                order.setQuantity(event.getQuantity());
                order.setStatus(OrderStatus.PENDING);
                log.info("Created new pending order {}", order.getId());
            }

            // 3. Update Order Status to PROCESSING
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
            log.info("Order {} status updated to PROCESSING", order.getId());

            // 4. Update Order Status to PROCESSED
            order.setStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);
            log.info("Order {} status updated to PROCESSED", order.getId());

            // 5. Publish OrderProcessedEvent
            OrderProcessedEvent processedEvent = new OrderProcessedEvent();
            processedEvent.setOrderId(order.getId());
            processedEvent.setStatus(OrderStatus.PROCESSED.name());
            processedEvent.setProcessedAt(Instant.now().toString());

            rabbitTemplate.convertAndSend(exchange, "order.processed", processedEvent);
            log.info("Published OrderProcessedEvent for order {}", order.getId());

        } catch (Exception e) {
            log.error("Transient or unexpected error processing order {}: {}", event.getOrderId(), e.getMessage());
            // This will trigger Spring AMQP retry mechanism. After max retries, it moves to DLQ because default-requeue-rejected is false or dlq is set in queue.
            throw e;
        }
    }
}
