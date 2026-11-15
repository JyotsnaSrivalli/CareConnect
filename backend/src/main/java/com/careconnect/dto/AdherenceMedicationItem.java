package com.careconnect.dto;

import com.careconnect.entity.MedicationType;
import com.careconnect.entity.QuantityUnit;

import java.math.BigDecimal;

/**
 * One medicine's line in the adherence summary.
 *
 * "confirmed / expected / remaining" are CONFIRMATIONS (what the parent clicked), counted in `unit`
 * ("tablets", "capsules" or "doses"). `physicalStock` is a completely different thing: how much medicine is
 * left in the box. Taking a tablet changes both, but they are never added together or mixed up.
 *
 * expected, remaining, completionPercent, extraConfirmations and timesPerDay are null for a past date,
 * because the schedule history is not stored (see AdherenceSummaryResponse.message).
 */
public record AdherenceMedicationItem(
        Long medicationId,
        String name,
        String strength,
        MedicationType type,
        String unit,
        BigDecimal dosagePerDose,
        QuantityUnit dosageUnit,
        Integer timesPerDay,
        int confirmed,
        Integer expected,
        Integer remaining,
        Integer completionPercent,
        Integer extraConfirmations,
        PhysicalStock physicalStock) {

    /** What is physically left in the box (tablets / capsules, or millilitres for syrup). */
    public record PhysicalStock(BigDecimal remaining, QuantityUnit unit, boolean lowStock) { }
}
