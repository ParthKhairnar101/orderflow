package com.parth.orderflow.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderQueryService {

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return list(false);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(boolean demoMode) {
        return orders.findAllByDemoModeOrderByCreatedAtDesc(demoMode).stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return get(id, false);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, boolean demoMode) {
        return orders.findWithItemsByIdAndDemoMode(id, demoMode).map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
