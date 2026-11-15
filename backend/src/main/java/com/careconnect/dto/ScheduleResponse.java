package com.careconnect.dto;

import com.careconnect.entity.MedicationSchedule;

import java.time.LocalTime;

public record ScheduleResponse(Long id, LocalTime doseTime) {
    public static ScheduleResponse from(MedicationSchedule s) {
        return new ScheduleResponse(s.getId(), s.getDoseTime());
    }
}
