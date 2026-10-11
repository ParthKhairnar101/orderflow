package com.parth.orderflow.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SetInventoryRequest(
        @NotNull @PositiveOrZero Integer availableQuantity
) {
}
