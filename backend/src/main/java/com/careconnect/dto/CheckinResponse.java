package com.careconnect.dto;

import com.careconnect.entity.DailyCheckin;
import com.careconnect.entity.EnergyLevel;
import com.careconnect.entity.Feeling;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the API returns. energyLevel can be null only for an old (pre-Day 5) row.
 * No user details, no password hash: just the check-in itself.
 */
public record CheckinResponse(
        Long id,
        LocalDate date,
        Feeling feeling,
        EnergyLevel energyLevel,
        String symptoms,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CheckinResponse from(DailyCheckin c) {
        // old rows have no updated_at yet, so fall back to the time they were created
        LocalDateTime updated = c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt();
        return new CheckinResponse(c.getId(), c.getCheckinDate(), c.getFeeling(), c.getEnergyLevel(),
                c.getSymptoms(), c.getNotes(), c.getCreatedAt(), updated);
    }
}
