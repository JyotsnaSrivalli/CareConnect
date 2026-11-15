package com.careconnect.dto;

import com.careconnect.entity.FoodInstruction;
import com.careconnect.entity.MedicationType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Used for both POST and PUT. The last three fields (initialQuantity, lowStockThreshold, doseTimes)
 * are only read when CREATING; afterwards stock and schedules have their own endpoints.
 * The parent id is never part of the request: it comes from the JWT.
 */
public record MedicationRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @NotNull(message = "Type is required (TABLET, CAPSULE or SYRUP)")
        MedicationType type,

        @Size(max = 50, message = "Strength must be at most 50 characters")
        String strength,

        @NotNull(message = "Dosage per dose is required")
        @DecimalMin(value = "0.01", message = "Dosage per dose must be greater than 0")
        @Digits(integer = 4, fraction = 2, message = "Dosage per dose has too many digits")
        BigDecimal dosagePerDose,

        @NotBlank(message = "Frequency is required (e.g. Twice daily)")
        @Size(max = 60, message = "Frequency must be at most 60 characters")
        String frequencyLabel,

        FoodInstruction foodInstruction,

        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate,

        @DecimalMin(value = "0", message = "Initial quantity cannot be negative")
        @Digits(integer = 6, fraction = 2, message = "Initial quantity has too many digits")
        BigDecimal initialQuantity,

        @DecimalMin(value = "0", message = "Low-stock threshold cannot be negative")
        @Digits(integer = 6, fraction = 2, message = "Low-stock threshold has too many digits")
        BigDecimal lowStockThreshold,

        @Size(max = 10, message = "At most 10 dose times")
        List<LocalTime> doseTimes) { }
