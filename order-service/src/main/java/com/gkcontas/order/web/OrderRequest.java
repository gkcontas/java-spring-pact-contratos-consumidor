package com.gkcontas.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderRequest(
        @NotBlank(message = "sku is required")
        String sku,
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        Integer quantity) {
}
