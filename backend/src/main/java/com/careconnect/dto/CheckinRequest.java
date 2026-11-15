package com.careconnect.dto;

import com.careconnect.entity.EnergyLevel;
import com.careconnect.entity.Feeling;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of POST /api/checkins. The parent id is never part of the request: it comes from the JWT.
 * Symptoms and notes are optional.
 */
public record CheckinRequest(
        @NotNull(message = "Date is required (format yyyy-MM-dd)")
        LocalDate date,

        @NotNull(message = "Overall feeling is required (GOOD, OKAY or NOT_GOOD)")
        Feeling feeling,

        @NotNull(message = "Energy level is required (HIGH, MODERATE or LOW)")
        EnergyLevel energyLevel,

        @Size(max = 500, message = "Symptoms must be at most 500 characters")
        String symptoms,

        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes) { }
