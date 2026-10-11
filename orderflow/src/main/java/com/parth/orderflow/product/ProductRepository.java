package com.parth.orderflow.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndDemoMode(UUID id, boolean demoMode);
    Optional<Product> findBySkuAndDemoMode(String sku, boolean demoMode);
    boolean existsBySkuAndDemoMode(String sku, boolean demoMode);
    List<Product> findAllByDemoModeOrderBySkuAsc(boolean demoMode);
    void deleteAllByDemoMode(boolean demoMode);
}
