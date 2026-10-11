package com.parth.orderflow.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Positive Integer quantity
) {
}
