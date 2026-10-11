package com.parth.orderflow.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderLifecycleService {

    private final OrderRepository orders;

    public OrderLifecycleService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        return cancel(id, false);
    }

    @Transactional
    public OrderResponse cancel(UUID id, boolean demoMode) {
        Order order = orders.findWithItemsByIdAndDemoMode(id, demoMode)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }
        order.getItems().forEach(item -> item.getProduct().restore(item.getQuantity()));
        order.cancel();
        return OrderResponse.from(orders.saveAndFlush(order));
    }
}
