package com.careconnect.dto;

import java.time.LocalDate;

public record SummaryResponse(LocalDate date, int totalDoses, int takenDoses, int pendingDoses, int completionPercent) { }
