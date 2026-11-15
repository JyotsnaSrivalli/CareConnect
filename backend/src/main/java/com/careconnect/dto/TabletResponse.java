package com.careconnect.dto;

import com.careconnect.entity.MedicationTablet;
import com.careconnect.entity.TabletStatus;

import java.time.LocalDateTime;

/** One compartment of the strip. takenAt is the date and time the parent confirmed it. */
public record TabletResponse(Long id, int slotNumber, TabletStatus status, LocalDateTime takenAt) {
    public static TabletResponse from(MedicationTablet t) {
        return new TabletResponse(t.getId(), t.getSlotNumber(), t.getStatus(), t.getTakenAt());
    }
}
