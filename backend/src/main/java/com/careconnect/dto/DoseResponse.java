package com.careconnect.dto;

import com.careconnect.entity.DoseStatus;
import com.careconnect.entity.QuantityUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** One scheduled dose on one day. logId/takenAt are null while the dose is PENDING. */
public record DoseResponse(
        Long medicationId,
        String medicationName,
        String strength,
        BigDecimal dosagePerDose,
        QuantityUnit quantityUnit,
        Long scheduleId,
        LocalTime scheduledTime,
        LocalDate scheduledDate,
        DoseStatus status,
        Long logId,
        LocalDateTime takenAt,
        BigDecimal remainingQuantity,
        boolean lowStock) { }
