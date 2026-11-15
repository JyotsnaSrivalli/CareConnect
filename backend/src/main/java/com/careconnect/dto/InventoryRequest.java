package com.careconnect.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryRequest(
        @NotNull(message = "Current quantity is required")
        @DecimalMin(value = "0", message = "Current quantity cannot be negative")
        @Digits(integer = 6, fraction = 2, message = "Current quantity has too many digits")
        BigDecimal currentQuantity,

        @NotNull(message = "Low-stock threshold is required")
        @DecimalMin(value = "0", message = "Low-stock threshold cannot be negative")
        @Digits(integer = 6, fraction = 2, message = "Low-stock threshold has too many digits")
        BigDecimal lowStockThreshold) { }
