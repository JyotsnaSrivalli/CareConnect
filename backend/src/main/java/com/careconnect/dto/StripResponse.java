package com.careconnect.dto;

import com.careconnect.entity.MedicationType;
import com.careconnect.entity.QuantityUnit;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * One blister strip = one medicine. totalTablets = number of compartments, remainingTablets = physical stock.
 * expectedToday is what the schedule asks for (dose times x tablets per dose); it is informational only:
 * an unconfirmed tablet is never "missed".
 */
public record StripResponse(
        Long medicationId,
        String medicationName,
        String strength,
        MedicationType type,
        QuantityUnit unit,
        BigDecimal dosagePerDose,
        int totalTablets,
        int takenTablets,
        int remainingTablets,
        BigDecimal lowStockThreshold,
        boolean lowStock,
        List<LocalTime> scheduleTimes,
        int expectedToday,
        int takenToday,
        List<TabletResponse> tablets) { }
