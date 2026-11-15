package com.careconnect.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Medication adherence summary for ONE date.
 *
 * Totals are counted in "confirmations": one tablet click or one syrup-dose click is one confirmation.
 * (That is the only unit tablets and syrup have in common; the per-medicine lines keep their own unit.)
 *
 * Today        : expectedAvailable = true, every field is filled in.
 * A past date  : expectedAvailable = false. Only confirmedTotal and the per-medicine `confirmed` counts are real;
 *                expected / remaining / completionPercent / extraConfirmations are null and `message` says why.
 *
 * completionPercent is also null when nothing was expected today (nothing to complete).
 */
public record AdherenceSummaryResponse(
        LocalDate date,
        boolean today,
        boolean expectedAvailable,
        String message,
        int confirmedTotal,
        Integer expectedTotal,
        Integer remainingTotal,
        Integer completionPercent,
        Integer extraConfirmations,
        List<AdherenceMedicationItem> medications) { }
