package com.gkcontas.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReservationRequest(
        @NotBlank(message = "sku is required")
        String sku,
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        Integer quantity,
        @NotBlank(message = "orderRef is required")
        @Size(max = 64, message = "orderRef must have at most 64 characters")
        String orderRef) {
}
