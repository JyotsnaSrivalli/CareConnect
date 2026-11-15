package com.careconnect.dto;

import com.careconnect.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MedicationResponse(
        Long id,
        Long parentId,
        String name,
        MedicationType type,
        String strength,
        BigDecimal dosagePerDose,
        QuantityUnit quantityUnit,
        String frequencyLabel,
        FoodInstruction foodInstruction,
        String notes,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        LocalDateTime createdAt,
        List<ScheduleResponse> schedules,
        InventoryResponse inventory) {

    public static MedicationResponse from(Medication m, List<MedicationSchedule> schedules, MedicationInventory inventory) {
        return new MedicationResponse(m.getId(), m.getParent().getId(), m.getName(), m.getType(), m.getStrength(),
                m.getDosagePerDose(), m.getQuantityUnit(), m.getFrequencyLabel(), m.getFoodInstruction(),
                m.getNotes(), m.getStartDate(), m.getEndDate(), m.isActive(), m.getCreatedAt(),
                schedules.stream().map(ScheduleResponse::from).toList(),
                inventory == null ? null : InventoryResponse.from(inventory));
    }
}
