package com.parth.orderflow.order;

import com.parth.orderflow.product.Product;
import com.parth.orderflow.product.ProductNotFoundException;
import com.parth.orderflow.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationTransaction {

    private final ProductRepository products;
    private final OrderRepository orders;

    public OrderCreationTransaction(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    @Transactional
    public OrderResponse create(String idempotencyKey, String requestHash, CreateOrderRequest request) {
        return create(idempotencyKey, requestHash, request, false);
    }

    @Transactional
    public OrderResponse create(String idempotencyKey, String requestHash, CreateOrderRequest request,
                                boolean demoMode) {
        Order order = Order.create(idempotencyKey, requestHash, demoMode);
        for (CreateOrderItemRequest line : request.items().stream()
                .sorted((left, right) -> left.productId().compareTo(right.productId())).toList()) {
            Product product = products.findByIdAndDemoMode(line.productId(), demoMode)
                    .orElseThrow(() -> new ProductNotFoundException(line.productId()));
            product.reserve(line.quantity());
            order.addItem(OrderItem.create(order, product, line.quantity()));
        }
        return OrderResponse.from(orders.saveAndFlush(order));
    }
}
