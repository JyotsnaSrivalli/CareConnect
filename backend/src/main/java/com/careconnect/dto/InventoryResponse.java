package com.careconnect.dto;

import com.careconnect.entity.MedicationInventory;
import com.careconnect.entity.QuantityUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryResponse(
        BigDecimal totalQuantity,
        BigDecimal currentQuantity,
        BigDecimal lowStockThreshold,
        QuantityUnit unit,
        boolean lowStock,
        LocalDateTime updatedAt) {

    public static InventoryResponse from(MedicationInventory i) {
        return new InventoryResponse(i.getTotalQuantity(), i.getCurrentQuantity(), i.getLowStockThreshold(),
                i.getMedication().getQuantityUnit(), i.isLowStock(), i.getUpdatedAt());
    }
}
