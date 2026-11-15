package com.careconnect.service;

import com.careconnect.dto.MedicationRequest;
import com.careconnect.dto.MedicationResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import com.careconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationInventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final FamilyService familyService;
    private final TabletService tabletService;

    public MedicationService(MedicationRepository medicationRepository,
                             MedicationScheduleRepository scheduleRepository,
                             MedicationInventoryRepository inventoryRepository,
                             UserRepository userRepository,
                             FamilyService familyService,
                             TabletService tabletService) {
        this.medicationRepository = medicationRepository;
        this.scheduleRepository = scheduleRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.familyService = familyService;
        this.tabletService = tabletService;
    }

    // ---------------- parent-only actions ----------------

    /** Creates the medication, its starting stock and (optionally) its dose times in ONE transaction. */
    @Transactional
    public MedicationResponse create(Long parentId, MedicationRequest request) {
        User parent = requireParent(parentId);
        validateDates(request);
        BigDecimal initialQuantity = orZero(request.initialQuantity());
        if (request.type().usesBlisterStrip()) {
            TabletService.toWholeTablets(initialQuantity);   // validate BEFORE saving anything: whole tablets, max 200
        }

        Medication medication = new Medication(parent, request.name().trim(), request.type(),
                blankToNull(request.strength()), request.dosagePerDose(), request.frequencyLabel().trim(),
                request.foodInstruction() == null ? FoodInstruction.ANY_TIME : request.foodInstruction(),
                blankToNull(request.notes()), request.startDate(), request.endDate());
        medicationRepository.save(medication);

        MedicationInventory inventory = new MedicationInventory(medication,
                initialQuantity, orZero(request.lowStockThreshold()));
        inventoryRepository.save(inventory);
        if (medication.getType().usesBlisterStrip()) {
            tabletService.createStrip(medication, initialQuantity);   // one compartment per tablet
        }

        List<MedicationSchedule> schedules = new ArrayList<>();
        if (request.doseTimes() != null) {
            // TreeSet = sorted + no duplicates (the DB also forbids the same time twice)
            TreeSet<LocalTime> times = new TreeSet<>();
            request.doseTimes().forEach(t -> times.add(normalize(t)));
            for (LocalTime time : times) {
                MedicationSchedule schedule = new MedicationSchedule(medication, time);
                scheduleRepository.save(schedule);
                schedules.add(schedule);
            }
        }
        return MedicationResponse.from(medication, schedules, inventory);
    }

    @Transactional
    public MedicationResponse update(Long parentId, Long medicationId, MedicationRequest request) {
        Medication medication = requireOwned(parentId, medicationId);
        validateDates(request);
        // tablets/capsules have a strip of compartments, syrup does not: switching would break the stock records
        if (medication.getType().usesBlisterStrip() != request.type().usesBlisterStrip()) {
            throw ApiException.badRequest("A medicine cannot be switched between tablet/capsule and syrup. Add it again instead.");
        }
        medication.updateDetails(request.name().trim(), request.type(), blankToNull(request.strength()),
                request.dosagePerDose(), request.frequencyLabel().trim(),
                request.foodInstruction() == null ? FoodInstruction.ANY_TIME : request.foodInstruction(),
                blankToNull(request.notes()), request.startDate(), request.endDate());
        return toResponses(List.of(medication)).get(0);   // Hibernate saves the changes when the transaction ends
    }

    /** Soft delete: the row stays (active=false) so dose history added on Day 4 is never orphaned. */
    @Transactional
    public void delete(Long parentId, Long medicationId) {
        requireOwned(parentId, medicationId).deactivate();
    }

    // ---------------- read actions (parent or approved child) ----------------

    @Transactional(readOnly = true)
    public List<MedicationResponse> list(Long userId, Role role, Long parentId) {
        Long ownerId;
        if (role == Role.PARENT) {
            ownerId = userId;                       // a parent only ever sees their own list
        } else {
            if (parentId == null) {
                throw ApiException.badRequest("parentId is required");
            }
            familyService.assertChildCanAccessParent(userId, parentId);
            ownerId = parentId;
        }
        return toResponses(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(ownerId));
    }

    @Transactional(readOnly = true)
    public MedicationResponse get(Long userId, Role role, Long medicationId) {
        return toResponses(List.of(requireViewable(userId, role, medicationId))).get(0);
    }

    // ---------------- shared guards (also used by schedule + inventory services) ----------------

    /** Medication must exist, be active and belong to THIS parent. Otherwise 404. */
    public Medication requireOwned(Long parentId, Long medicationId) {
        return medicationRepository.findByIdAndParentIdAndActiveTrue(medicationId, parentId)
                .orElseThrow(() -> ApiException.notFound("Medication not found"));
    }

    /** Parent: must own it. Child: needs an APPROVED connection with the owning parent (else 403). */
    public Medication requireViewable(Long userId, Role role, Long medicationId) {
        if (role == Role.PARENT) {
            return requireOwned(userId, medicationId);
        }
        Medication medication = medicationRepository.findByIdAndActiveTrue(medicationId)
                .orElseThrow(() -> ApiException.notFound("Medication not found"));
        familyService.assertChildCanAccessParent(userId, medication.getParent().getId());
        return medication;
    }

    // ---------------- helpers ----------------

    private User requireParent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        if (user.getRole() != Role.PARENT) {
            throw ApiException.forbidden("Only parents can manage medications");
        }
        return user;
    }

    private void validateDates(MedicationRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw ApiException.badRequest("End date cannot be before start date");
        }
    }

    /** Loads schedules and inventory for ALL medications in 2 queries (instead of 2 per medication). */
    private List<MedicationResponse> toResponses(List<Medication> medications) {
        if (medications.isEmpty()) return List.of();
        List<Long> ids = medications.stream().map(Medication::getId).toList();

        Map<Long, List<MedicationSchedule>> schedulesByMedication =
                scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(ids).stream()
                        .collect(Collectors.groupingBy(s -> s.getMedication().getId()));
        Map<Long, MedicationInventory> inventoryByMedication =
                inventoryRepository.findByMedicationIdIn(ids).stream()
                        .collect(Collectors.toMap(i -> i.getMedication().getId(), Function.identity()));

        return medications.stream()
                .map(m -> MedicationResponse.from(m,
                        schedulesByMedication.getOrDefault(m.getId(), List.of()),
                        inventoryByMedication.get(m.getId())))
                .toList();
    }

    static LocalTime normalize(LocalTime time) {
        return time.withSecond(0).withNano(0);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
