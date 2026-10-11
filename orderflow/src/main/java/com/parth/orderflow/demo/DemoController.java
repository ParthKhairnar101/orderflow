package com.parth.orderflow.demo;

import com.parth.orderflow.order.CreateOrderRequest;
import com.parth.orderflow.order.OrderLifecycleService;
import com.parth.orderflow.order.OrderQueryService;
import com.parth.orderflow.order.OrderResponse;
import com.parth.orderflow.order.OrderService;
import com.parth.orderflow.product.CreateProductRequest;
import com.parth.orderflow.product.ProductResponse;
import com.parth.orderflow.product.ProductService;
import com.parth.orderflow.product.SetInventoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoPlaygroundService playground;
    private final ProductService products;
    private final OrderService orders;
    private final OrderQueryService orderQueries;
    private final OrderLifecycleService orderLifecycle;

    public DemoController(DemoPlaygroundService playground, ProductService products,
                          OrderService orders, OrderQueryService orderQueries,
                          OrderLifecycleService orderLifecycle) {
        this.playground = playground;
        this.products = products;
        this.orders = orders;
        this.orderQueries = orderQueries;
        this.orderLifecycle = orderLifecycle;
    }

    @GetMapping("/status")
    public DemoStatusResponse status() {
        return playground.status();
    }

    @PostMapping("/reset")
    public DemoStatusResponse reset() {
        return playground.reset();
    }

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return playground.interact(() -> products.list(true));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = playground.interact(() -> products.create(request, true));
        return ResponseEntity.created(URI.create("/api/v1/demo/products/" + created.id())).body(created);
    }

    @PatchMapping("/products/{id}/inventory")
    public ProductResponse setInventory(@PathVariable UUID id,
                                        @Valid @RequestBody SetInventoryRequest request) {
        return playground.interact(() -> products.setInventory(id, request.availableQuantity(), true));
    }

    @GetMapping("/orders")
    public List<OrderResponse> orders() {
        return playground.interact(() -> orderQueries.list(true));
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = playground.interact(() -> orders.create(idempotencyKey, request, true));
        return ResponseEntity.created(URI.create("/api/v1/demo/orders/" + created.id())).body(created);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return playground.interact(() -> orderLifecycle.cancel(id, true));
    }
}
