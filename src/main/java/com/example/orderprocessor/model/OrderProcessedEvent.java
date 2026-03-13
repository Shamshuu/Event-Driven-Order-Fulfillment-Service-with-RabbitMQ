package com.example.orderprocessor.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderProcessedEvent {
    private String orderId;
    private String status;
    private String processedAt;
}
