package com.parth.orderflow.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdempotencyKeyAndDemoMode(String idempotencyKey, boolean demoMode);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByIdAndDemoMode(UUID id, boolean demoMode);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByDemoModeOrderByCreatedAtDesc(boolean demoMode);
    void deleteAllByDemoMode(boolean demoMode);
}
