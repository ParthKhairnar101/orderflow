package com.parth.orderflow.demo;

import com.parth.orderflow.product.CreateProductRequest;
import com.parth.orderflow.product.ProductResponse;
import com.parth.orderflow.product.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Service
public class DemoPlaygroundService {

    private final JdbcTemplate jdbc;
    private final ProductService products;
    private final Duration idleTtl;

    public DemoPlaygroundService(
            JdbcTemplate jdbc,
            ProductService products,
            @Value("${orderflow.demo.idle-ttl:PT20M}") Duration idleTtl) {
        this.jdbc = jdbc;
        this.products = products;
        this.idleTtl = idleTtl;
    }

    @Transactional
    public <T> T interact(Supplier<T> operation) {
        Instant now = lockAndReadLastActivity();
        resetIfExpired(now);
        seedIfEmpty();
        T result = operation.get();
        touch();
        return result;
    }

    @Transactional
    public DemoStatusResponse status() {
        return interact(() -> new DemoStatusResponse(Instant.now().plus(idleTtl), idleTtl.toMinutes()));
    }

    @Transactional
    public DemoStatusResponse reset() {
        lockAndReadLastActivity();
        resetData();
        touch();
        return new DemoStatusResponse(Instant.now().plus(idleTtl), idleTtl.toMinutes());
    }

    @Scheduled(fixedDelayString = "${orderflow.demo.cleanup-interval:PT1M}")
    @Transactional
    public void resetAfterInactivity() {
        Instant lastActivity = lockAndReadLastActivity();
        if (!lastActivity.plus(idleTtl).isAfter(Instant.now())) {
            resetData();
            touch();
        }
    }

    private Instant lockAndReadLastActivity() {
        Timestamp timestamp = jdbc.queryForObject(
                "SELECT last_activity_at FROM demo_activity WHERE id = 1 FOR UPDATE", Timestamp.class);
        return timestamp.toInstant();
    }

    private void resetIfExpired(Instant lastActivity) {
        if (!lastActivity.plus(idleTtl).isAfter(Instant.now())) {
            resetData();
        }
    }

    private void resetData() {
        jdbc.update("DELETE FROM order_items WHERE order_id IN "
                + "(SELECT id FROM orders WHERE demo_mode = TRUE)");
        jdbc.update("DELETE FROM orders WHERE demo_mode = TRUE");
        jdbc.update("DELETE FROM products WHERE demo_mode = TRUE");
        seedDefaults();
    }

    private void seedIfEmpty() {
        if (products.list(true).isEmpty()) {
            seedDefaults();
        }
    }

    private void seedDefaults() {
        List<CreateProductRequest> defaults = List.of(
                new CreateProductRequest("DEMO-KEY-1001", "Studio Keyboard",
                        "A tactile mechanical keyboard for a focused desk setup.", new BigDecimal("79.99"), 18),
                new CreateProductRequest("DEMO-LAMP-2001", "Arc Desk Lamp",
                        "Dimmable warm lighting with a compact aluminum base.", new BigDecimal("46.50"), 12),
                new CreateProductRequest("DEMO-HUB-3001", "Everyday USB-C Hub",
                        "A six-port hub for a tidy, connected workspace.", new BigDecimal("34.00"), 24)
        );
        defaults.forEach(request -> products.create(request, true));
    }

    private void touch() {
        jdbc.update("UPDATE demo_activity SET last_activity_at = ? WHERE id = 1",
                Timestamp.from(Instant.now()));
    }
}
