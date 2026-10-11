package com.parth.orderflow.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints =
        @UniqueConstraint(name = "products_demo_mode_sku_unique", columnNames = {"demo_mode", "sku"}))
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "demo_mode", nullable = false)
    private boolean demoMode;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public static Product create(String sku, String name, String description,
                                 BigDecimal unitPrice, int availableQuantity) {
        return create(sku, name, description, unitPrice, availableQuantity, false);
    }

    public static Product create(String sku, String name, String description,
                                 BigDecimal unitPrice, int availableQuantity, boolean demoMode) {
        Product product = new Product();
        product.sku = sku;
        product.name = name;
        product.description = description;
        product.unitPrice = unitPrice;
        product.availableQuantity = availableQuantity;
        product.demoMode = demoMode;
        return product;
    }

    public void changeAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive.");
        }
        if (availableQuantity < quantity) {
            throw new InsufficientInventoryException(sku, availableQuantity, quantity);
        }
        availableQuantity -= quantity;
    }

    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restored quantity must be positive.");
        }
        availableQuantity = Math.addExact(availableQuantity, quantity);
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public boolean isDemoMode() { return demoMode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getAvailableQuantity() { return availableQuantity; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
