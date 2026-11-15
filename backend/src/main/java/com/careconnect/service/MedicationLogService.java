package com.careconnect.service;

import com.careconnect.dto.DoseResponse;
import com.careconnect.dto.SummaryResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationLogRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dose tracking for SYRUP (measured in ml). Tablets and capsules are tracked tablet-by-tablet on the blister
 * strip instead (TabletService), so this service ignores and refuses them: otherwise it would change stock
 * without changing any compartment. Key rules:
 *  - a dose is PENDING until the parent confirms it; time passing never changes it (there is no "missed")
 *  - confirming writes the log AND deducts stock in one transaction
 *  - undo marks the log UNDONE (row kept) and gives back exactly what was deducted
 */
@Service
public class MedicationLogService {

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationInventoryRepository inventoryRepository;
    private final MedicationLogRepository logRepository;
    private final FamilyService familyService;
    private final Clock clock;

    public MedicationLogService(MedicationRepository medicationRepository,
                                MedicationScheduleRepository scheduleRepository,
                                MedicationInventoryRepository inventoryRepository,
                                MedicationLogRepository logRepository,
                                FamilyService familyService,
                                Clock clock) {
        this.medicationRepository = medicationRepository;
        this.scheduleRepository = scheduleRepository;
        this.inventoryRepository = inventoryRepository;
        this.logRepository = logRepository;
        this.familyService = familyService;
        this.clock = clock;
    }

    // ================= reading (parent: own data, child: approved parent only) =================

    /** date == null means today. */
    @Transactional(readOnly = true)
    public List<DoseResponse> doses(Long userId, Role role, Long parentId, LocalDate date) {
        LocalDate day = resolveDate(date);
        return buildDoses(resolveOwner(userId, role, parentId), day);
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId, Role role, Long parentId, LocalDate date) {
        LocalDate day = resolveDate(date);
        List<DoseResponse> doses = buildDoses(resolveOwner(userId, role, parentId), day);
        int total = doses.size();
        int taken = (int) doses.stream().filter(d -> d.status() == DoseStatus.TAKEN).count();
        int percent = total == 0 ? 0 : (int) Math.round(taken * 100.0 / total);
        return new SummaryResponse(day, total, taken, total - taken, percent);
    }

    // ================= confirming a dose =================

    @Transactional
    public DoseResponse markTaken(Long parentId, Long scheduleId, LocalDate requestedDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = requestedDate == null ? today : requestedDate;
        if (date.isAfter(today)) {
            throw ApiException.badRequest("A dose in the future cannot be confirmed");
        }

        // Ownership + "medication and schedule are active" are part of this one query
        MedicationSchedule schedule = scheduleRepository.findActiveOwned(scheduleId, parentId)
                .orElseThrow(() -> ApiException.notFound("Schedule not found"));
        Medication medication = schedule.getMedication();
        requireSyrup(medication);
        if (!isScheduledOn(medication, date)) {
            throw ApiException.badRequest("This medication is not scheduled on " + date);
        }

        // 1) Lock the stock row first. Everything below now runs alone for this medication.
        MedicationInventory inventory = inventoryRepository.findByMedicationIdForUpdate(medication.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory not found"));

        // 2) Already confirmed? (locking read = sees the latest committed data)
        Optional<MedicationLog> existing = logRepository.findForUpdate(schedule.getId(), date);
        if (existing.isPresent() && existing.get().getStatus() == LogStatus.TAKEN) {
            throw ApiException.conflict("This dose is already marked as taken");
        }

        // 3) Enough stock? Checked BEFORE anything is changed or saved, so a failure leaves no partial data.
        BigDecimal quantity = medication.getDosagePerDose();
        if (!inventory.hasAtLeast(quantity)) {
            throw ApiException.conflict("Not enough stock: this dose needs " + quantity
                    + " but only " + inventory.getCurrentQuantity() + " left");
        }

        // 4) Deduct stock and record the dose (both commit together or neither does)
        LocalDateTime now = LocalDateTime.now(clock);
        inventory.deduct(quantity);
        inventoryRepository.save(inventory);

        MedicationLog log;
        if (existing.isPresent()) {            // confirmed before, then undone: reuse the row
            log = existing.get();
            log.retake(quantity, now);
        } else {
            log = new MedicationLog(medication, schedule, date, quantity, now);
        }
        logRepository.save(log);

        return toDose(medication, schedule, date, log, inventory);
    }

    // ================= undoing a confirmation =================

    @Transactional
    public DoseResponse undo(Long parentId, Long logId) {
        // Ownership first. Returns only the medication id (no entity cached, so no stale state later).
        Long medicationId = logRepository.findMedicationIdOwnedBy(logId, parentId)
                .orElseThrow(() -> ApiException.notFound("Log not found"));

        // Same lock order as markTaken (stock first, then log) so the two can never deadlock
        MedicationInventory inventory = inventoryRepository.findByMedicationIdForUpdate(medicationId)
                .orElseThrow(() -> ApiException.notFound("Inventory not found"));
        MedicationLog log = logRepository.findByIdForUpdate(logId)
                .orElseThrow(() -> ApiException.notFound("Log not found"));

        requireSyrup(log.getMedication());
        if (log.getStatus() == LogStatus.UNDONE) {
            throw ApiException.conflict("This dose was already undone");   // stock is never restored twice
        }

        inventory.restore(log.getQuantityDeducted());
        inventoryRepository.save(inventory);
        log.markUndone(LocalDateTime.now(clock));
        logRepository.save(log);

        return toDose(log.getMedication(), log.getSchedule(), log.getScheduledDate(), log, inventory);
    }

    // ================= helpers =================

    private LocalDate resolveDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today)) {
            throw ApiException.badRequest("Date cannot be in the future");
        }
        return day;
    }

    /** Parent: always themselves. Child: must name a parent and have an APPROVED connection. */
    private Long resolveOwner(Long userId, Role role, Long parentId) {
        if (role == Role.PARENT) {
            return userId;
        }
        if (parentId == null) {
            throw ApiException.badRequest("parentId is required");
        }
        familyService.assertChildCanAccessParent(userId, parentId);
        return parentId;
    }

    private static void requireSyrup(Medication medication) {
        if (medication.getType() != MedicationType.SYRUP) {
            throw ApiException.badRequest("Tablets and capsules are confirmed one by one on the blister strip");
        }
    }

    private static boolean isScheduledOn(Medication m, LocalDate date) {
        return !date.isBefore(m.getStartDate()) && (m.getEndDate() == null || !date.isAfter(m.getEndDate()));
    }

    /**
     * Pending doses are NOT stored: they are generated on the fly from active schedules.
     * Confirmed doses come from the logs, so history stays visible even after a schedule is removed.
     */
    private List<DoseResponse> buildDoses(Long parentId, LocalDate date) {
        List<Medication> medications = medicationRepository
                .findByParentIdAndActiveTrueOrderByCreatedAtDesc(parentId).stream()
                .filter(m -> m.getType() == MedicationType.SYRUP)
                .filter(m -> isScheduledOn(m, date))
                .toList();
        List<Long> medicationIds = medications.stream().map(Medication::getId).toList();

        List<MedicationSchedule> schedules = medicationIds.isEmpty() ? List.of()
                : scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(medicationIds);
        List<MedicationLog> takenLogs = logRepository.findByParentAndDateAndStatus(parentId, date, LogStatus.TAKEN).stream()
                .filter(l -> l.getMedication().getType() == MedicationType.SYRUP)   // old tablet logs stay in the DB, not shown
                .toList();
        Map<Long, MedicationLog> logBySchedule = takenLogs.stream()
                .collect(Collectors.toMap(l -> l.getSchedule().getId(), Function.identity()));

        Set<Long> allMedicationIds = new HashSet<>(medicationIds);
        takenLogs.forEach(l -> allMedicationIds.add(l.getMedication().getId()));
        Map<Long, MedicationInventory> inventoryByMedication = allMedicationIds.isEmpty() ? Map.of()
                : inventoryRepository.findByMedicationIdIn(allMedicationIds).stream()
                .collect(Collectors.toMap(i -> i.getMedication().getId(), Function.identity()));

        List<DoseResponse> doses = new ArrayList<>();
        Set<Long> covered = new HashSet<>();
        for (MedicationSchedule s : schedules) {
            Medication m = s.getMedication();
            doses.add(toDose(m, s, date, logBySchedule.get(s.getId()), inventoryByMedication.get(m.getId())));
            covered.add(s.getId());
        }
        for (MedicationLog l : takenLogs) {            // confirmed doses of schedules that are no longer active
            if (!covered.contains(l.getSchedule().getId())) {
                Medication m = l.getMedication();
                doses.add(toDose(m, l.getSchedule(), date, l, inventoryByMedication.get(m.getId())));
            }
        }
        doses.sort(Comparator.comparing(DoseResponse::scheduledTime).thenComparing(DoseResponse::medicationName));
        return doses;
    }

    private DoseResponse toDose(Medication m, MedicationSchedule s, LocalDate date, MedicationLog log,
                                MedicationInventory inventory) {
        boolean taken = log != null && log.getStatus() == LogStatus.TAKEN;
        return new DoseResponse(m.getId(), m.getName(), m.getStrength(), m.getDosagePerDose(), m.getQuantityUnit(),
                s.getId(), s.getDoseTime(), date,
                taken ? DoseStatus.TAKEN : DoseStatus.PENDING,
                taken ? log.getId() : null,
                taken ? log.getTakenAt() : null,
                inventory == null ? null : inventory.getCurrentQuantity(),
                inventory != null && inventory.isLowStock());
    }
}
