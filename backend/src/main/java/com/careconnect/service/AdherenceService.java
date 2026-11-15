package com.careconnect.service;

import com.careconnect.dto.AdherenceMedicationItem;
import com.careconnect.dto.AdherenceMedicationItem.PhysicalStock;
import com.careconnect.dto.AdherenceSummaryResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationLogRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import com.careconnect.repository.MedicationTabletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Medication adherence summary (Day 5). READ-ONLY: it never marks anything taken or missed and never changes stock.
 *
 * Where the numbers come from (the Day 4 data model):
 *  - Tablets / capsules: one `medication_tablets` row per physical tablet. A confirmed tablet is TAKEN with a
 *    `taken_at` time, so "confirmed on date D" = tablets TAKEN with taken_at on D. A tablet row is NOT linked to a
 *    schedule, so we never say "this tablet was the 8 am dose": we only compare COUNTS.
 *  - Syrup: one `medication_logs` row per (schedule, date). "Confirmed on date D" = TAKEN logs for D.
 *  - Old medication_logs rows of tablet medicines still exist in the database. They are ignored here, otherwise
 *    a tablet would be counted twice (once as a tablet, once as an old log).
 *
 * Expected amounts come from TODAY's active schedules. The database does not remember what the schedule was on an
 * earlier day (schedules and dosages are edited in place), so for past dates we only report what was confirmed.
 */
@Service
public class AdherenceService {

    static final String PAST_DATE_MESSAGE = "Expected doses and completion are only shown for today. CareConnect does not "
            + "keep a history of past schedules, so today's schedule cannot be used to judge what was due on an earlier "
            + "day. This page shows only what was actually confirmed on that date.";

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationInventoryRepository inventoryRepository;
    private final MedicationTabletRepository tabletRepository;
    private final MedicationLogRepository logRepository;
    private final FamilyService familyService;
    private final Clock clock;

    public AdherenceService(MedicationRepository medicationRepository,
                            MedicationScheduleRepository scheduleRepository,
                            MedicationInventoryRepository inventoryRepository,
                            MedicationTabletRepository tabletRepository,
                            MedicationLogRepository logRepository,
                            FamilyService familyService,
                            Clock clock) {
        this.medicationRepository = medicationRepository;
        this.scheduleRepository = scheduleRepository;
        this.inventoryRepository = inventoryRepository;
        this.tabletRepository = tabletRepository;
        this.logRepository = logRepository;
        this.familyService = familyService;
        this.clock = clock;
    }

    /** date == null means today. Parent: own data. Child: add the parentId of an APPROVED parent. */
    @Transactional(readOnly = true)
    public AdherenceSummaryResponse summary(Long callerId, Role role, Long parentId, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today)) {
            throw ApiException.badRequest("Date cannot be in the future");
        }
        Long ownerId = resolveOwner(callerId, role, parentId);
        boolean isToday = day.equals(today);

        // Same list the dashboard shows: the parent's ACTIVE medicines (removed medicines are hidden everywhere)
        List<Medication> medications = medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(ownerId);
        if (medications.isEmpty()) {
            return build(day, isToday, List.of());
        }
        List<Long> ids = medications.stream().map(Medication::getId).toList();

        Map<Long, MedicationInventory> stockByMedication = inventoryRepository.findByMedicationIdIn(ids).stream()
                .collect(Collectors.toMap(i -> i.getMedication().getId(), Function.identity()));

        // Active schedules are only needed for today's "expected" numbers
        Map<Long, List<MedicationSchedule>> schedulesByMedication = isToday
                ? scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(ids).stream()
                        .collect(Collectors.groupingBy(s -> s.getMedication().getId()))
                : Map.of();

        // Confirmed syrup doses for the day, grouped by medicine. Only SYRUP medicines from the active list count.
        Set<Long> syrupIds = medications.stream()
                .filter(m -> m.getType() == MedicationType.SYRUP).map(Medication::getId).collect(Collectors.toSet());
        Map<Long, List<MedicationLog>> syrupLogsByMedication = logRepository
                .findByParentAndDateAndStatus(ownerId, day, LogStatus.TAKEN).stream()
                .filter(l -> syrupIds.contains(l.getMedication().getId()))
                .collect(Collectors.groupingBy(l -> l.getMedication().getId()));

        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();

        List<AdherenceMedicationItem> items = new ArrayList<>();
        for (Medication m : medications) {
            List<MedicationSchedule> schedules = schedulesByMedication.getOrDefault(m.getId(), List.of());
            AdherenceMedicationItem item;
            if (m.getType().usesBlisterStrip()) {
                int confirmed = (int) tabletRepository.countByStatusBetween(m.getId(), TabletStatus.TAKEN, from, to);
                item = tabletItem(m, confirmed, schedules.size(), isToday && isScheduledOn(m, day), isToday,
                        stockByMedication.get(m.getId()));
            } else {
                List<MedicationLog> logs = syrupLogsByMedication.getOrDefault(m.getId(), List.of());
                item = syrupItem(m, logs, schedules, isToday && isScheduledOn(m, day), isToday,
                        stockByMedication.get(m.getId()));
            }
            // Past date: list only medicines that were confirmed that day. Today: also medicines that are due.
            boolean worthShowing = item.confirmed() > 0 || (item.expected() != null && item.expected() > 0);
            if (worthShowing) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(AdherenceMedicationItem::name, String.CASE_INSENSITIVE_ORDER));
        return build(day, isToday, items);
    }

    // ================= one medicine =================

    /**
     * Tablet / capsule. expected = tablets due today = dosage per dose x number of active schedules
     * (rounded up, the same rule as the strip header). `counted` is what counts towards completion:
     * never more than expected, so extra tablets cannot push remaining below 0 or the percentage above 100.
     */
    private AdherenceMedicationItem tabletItem(Medication m, int confirmed, int scheduleCount, boolean dueToday,
                                               boolean isToday, MedicationInventory stock) {
        if (!isToday) {
            return item(m, unitName(m), confirmed, null, null, null, stock);
        }
        int expected = dueToday ? expectedTablets(m.getDosagePerDose(), scheduleCount) : 0;
        int counted = Math.min(confirmed, expected);
        return item(m, unitName(m), confirmed, expected, counted, scheduleCount, stock);
    }

    /**
     * Syrup. expected = one dose per active schedule. A schedule can have at most one TAKEN log per date, so
     * logs of ACTIVE schedules never exceed expected. A TAKEN log whose schedule was removed later is still a real
     * confirmation (shown in `confirmed`) but it is not one of today's doses, so it does not count towards completion.
     */
    private AdherenceMedicationItem syrupItem(Medication m, List<MedicationLog> logs, List<MedicationSchedule> schedules,
                                              boolean dueToday, boolean isToday, MedicationInventory stock) {
        int confirmed = logs.size();
        if (!isToday) {
            return item(m, "doses", confirmed, null, null, null, stock);
        }
        Set<Long> activeScheduleIds = schedules.stream().map(MedicationSchedule::getId).collect(Collectors.toSet());
        int expected = dueToday ? schedules.size() : 0;
        int counted = (int) logs.stream().filter(l -> activeScheduleIds.contains(l.getSchedule().getId())).count();
        counted = Math.min(counted, expected);
        return item(m, "doses", confirmed, expected, counted, schedules.size(), stock);
    }

    /**
     * Turns the raw numbers into the response line. expected and counted are null for a past date.
     *   remaining = expected - counted   (counted is never above expected, so this is never negative)
     *   extra     = confirmed - counted  (confirmations beyond what was expected)
     *   percent   = counted / expected   (null when nothing was expected)
     */
    private AdherenceMedicationItem item(Medication m, String unit, int confirmed, Integer expected, Integer counted,
                                         Integer timesPerDay, MedicationInventory stock) {
        Integer remaining = null;
        Integer percent = null;
        Integer extra = null;
        if (expected != null) {
            remaining = expected - counted;
            extra = confirmed - counted;
            percent = expected == 0 ? null : percentOf(counted, expected);
        }
        PhysicalStock physical = stock == null ? null
                : new PhysicalStock(stock.getCurrentQuantity(), m.getQuantityUnit(), stock.isLowStock());
        return new AdherenceMedicationItem(m.getId(), m.getName(), m.getStrength(), m.getType(), unit,
                m.getDosagePerDose(), m.getQuantityUnit(), timesPerDay, confirmed, expected, remaining, percent,
                extra, physical);
    }

    // ================= totals =================

    private AdherenceSummaryResponse build(LocalDate day, boolean isToday, List<AdherenceMedicationItem> items) {
        int confirmedTotal = items.stream().mapToInt(AdherenceMedicationItem::confirmed).sum();
        if (!isToday) {
            return new AdherenceSummaryResponse(day, false, false, PAST_DATE_MESSAGE, confirmedTotal,
                    null, null, null, null, items);
        }
        int expectedTotal = items.stream().mapToInt(AdherenceMedicationItem::expected).sum();
        int remainingTotal = items.stream().mapToInt(AdherenceMedicationItem::remaining).sum();
        int extraTotal = items.stream().mapToInt(AdherenceMedicationItem::extraConfirmations).sum();
        // Completion uses each medicine's CAPPED count (expected - remaining), so extra tablets of one medicine can
        // never hide a dose that is still pending for another medicine.
        int countedTotal = expectedTotal - remainingTotal;
        Integer percent = expectedTotal == 0 ? null : percentOf(countedTotal, expectedTotal);
        return new AdherenceSummaryResponse(day, true, true, null, confirmedTotal,
                expectedTotal, remainingTotal, percent, extraTotal, items);
    }

    // ================= small helpers =================

    /** Tablets due per day: dosage per dose x doses per day, rounded UP (same rule as the Day 4 strip header). */
    static int expectedTablets(BigDecimal dosagePerDose, int scheduleCount) {
        return dosagePerDose.multiply(BigDecimal.valueOf(scheduleCount)).setScale(0, RoundingMode.CEILING).intValue();
    }

    /** counted / expected as a whole percent, never above 100. Only call with expected > 0. */
    static int percentOf(int counted, int expected) {
        return Math.min(100, (int) Math.round(counted * 100.0 / expected));
    }

    private static String unitName(Medication m) {
        return m.getType() == MedicationType.CAPSULE ? "capsules" : "tablets";
    }

    private static boolean isScheduledOn(Medication m, LocalDate date) {
        return !date.isBefore(m.getStartDate()) && (m.getEndDate() == null || !date.isAfter(m.getEndDate()));
    }

    /** Parent: always themselves. Child: must name a parent and have an APPROVED connection. */
    private Long resolveOwner(Long callerId, Role role, Long parentId) {
        if (role == Role.PARENT) {
            return callerId;
        }
        if (parentId == null) {
            throw ApiException.badRequest("parentId is required");
        }
        familyService.assertChildCanAccessParent(callerId, parentId);
        return parentId;
    }
}
