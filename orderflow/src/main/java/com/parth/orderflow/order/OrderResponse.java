package com.parth.orderflow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String idempotencyKey,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant cancelledAt
) {
    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getIdempotencyKey(), order.getStatus(), order.getTotalAmount(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getVersion(), order.getCreatedAt(), order.getUpdatedAt(), order.getCancelledAt());
    }
}
