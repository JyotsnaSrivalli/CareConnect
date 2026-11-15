package com.careconnect.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ScheduleRequest(@NotNull(message = "Dose time is required (HH:mm)") LocalTime doseTime) { }
