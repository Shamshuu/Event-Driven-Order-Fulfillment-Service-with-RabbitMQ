package com.example.orderprocessor.service;

import com.example.orderprocessor.model.Order;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.example.orderprocessor.model.OrderProcessedEvent;
import com.example.orderprocessor.model.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderProcessingService, "exchange", "order.events");
    }

    @Test
    void processOrderPlacedEvent_ValidEvent_Succeeds() {
        OrderPlacedEvent event = new OrderPlacedEvent("order1", "prod1", 1, "cust1", "timestamp");
        when(orderRepository.findById("order1")).thenReturn(Optional.empty());

        orderProcessingService.processOrderPlacedEvent(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());
        
        Order savedOrder = orderCaptor.getAllValues().get(1);
        assertEquals("order1", savedOrder.getId());
        assertEquals(OrderStatus.PROCESSED, savedOrder.getStatus());

        ArgumentCaptor<OrderProcessedEvent> eventCaptor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq("order.events"), eq("order.processed"), eventCaptor.capture());
        
        OrderProcessedEvent sentEvent = eventCaptor.getValue();
        assertEquals("order1", sentEvent.getOrderId());
        assertEquals("PROCESSED", sentEvent.getStatus());
    }

    @Test
    void processOrderPlacedEvent_DuplicateEvent_Ignored() {
        OrderPlacedEvent event = new OrderPlacedEvent("order1", "prod1", 1, "cust1", "timestamp");
        Order existingOrder = new Order();
        existingOrder.setId("order1");
        existingOrder.setStatus(OrderStatus.PROCESSED);
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existingOrder));

        orderProcessingService.processOrderPlacedEvent(event);

        verify(orderRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void processOrderPlacedEvent_InvalidEvent_ThrowsRejectContentException() {
        OrderPlacedEvent event = new OrderPlacedEvent(null, "prod1", 1, "cust1", "timestamp");

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> {
            orderProcessingService.processOrderPlacedEvent(event);
        });

        verify(orderRepository, never()).save(any());
    }
}
