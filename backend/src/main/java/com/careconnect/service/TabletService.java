package com.careconnect.service;

import com.careconnect.dto.StripResponse;
import com.careconnect.dto.TabletResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Blister strips for tablet and capsule medicines.
 *  - one MedicationTablet row = one compartment = one physical tablet = one confirmation
 *  - taking a tablet marks that row TAKEN (with date+time) AND removes one unit of stock, in one transaction
 *  - undo makes that exact row AVAILABLE again AND gives one unit back
 *  - invariant: remaining stock == number of AVAILABLE compartments, total == number of compartments
 */
@Service
public class TabletService {

    static final int MAX_TABLETS = 200;

    private final MedicationRepository medicationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationInventoryRepository inventoryRepository;
    private final MedicationTabletRepository tabletRepository;
    private final FamilyService familyService;
    private final Clock clock;

    public TabletService(MedicationRepository medicationRepository,
                         MedicationScheduleRepository scheduleRepository,
                         MedicationInventoryRepository inventoryRepository,
                         MedicationTabletRepository tabletRepository,
                         FamilyService familyService,
                         Clock clock) {
        this.medicationRepository = medicationRepository;
        this.scheduleRepository = scheduleRepository;
        this.inventoryRepository = inventoryRepository;
        this.tabletRepository = tabletRepository;
        this.familyService = familyService;
        this.clock = clock;
    }

    // ================= reading (parent: own strips, child: approved parent only) =================

    @Transactional(readOnly = true)
    public List<StripResponse> strips(Long userId, Role role, Long parentId) {
        Long ownerId = resolveOwner(userId, role, parentId);
        List<Medication> medications = medicationRepository
                .findByParentIdAndActiveTrueOrderByCreatedAtDesc(ownerId).stream()
                .filter(m -> m.getType().usesBlisterStrip())
                .toList();
        return buildStrips(medications);
    }

    // ================= taking and undoing ONE tablet =================

    @Transactional
    public StripResponse take(Long parentId, Long tabletId) {
        // Ownership first (returns only an id). Then lock stock BEFORE the tablet row, same order everywhere.
        Long medicationId = tabletRepository.findMedicationIdOwnedBy(tabletId, parentId)
                .orElseThrow(() -> ApiException.notFound("Tablet not found"));
        MedicationInventory inventory = lockInventory(medicationId);
        MedicationTablet tablet = tabletRepository.findByIdForUpdate(tabletId)
                .orElseThrow(() -> ApiException.notFound("Tablet not found"));

        if (tablet.isTaken()) {
            throw ApiException.conflict("This tablet is already marked as taken");
        }
        if (!inventory.hasAtLeast(BigDecimal.ONE)) {
            throw ApiException.conflict("No stock left to deduct");
        }

        // checks done: now change both together (they commit together or not at all)
        inventory.deduct(BigDecimal.ONE);
        tablet.take(LocalDateTime.now(clock));
        inventoryRepository.save(inventory);
        tabletRepository.save(tablet);

        return buildStrips(List.of(tablet.getMedication())).get(0);
    }

    @Transactional
    public StripResponse undo(Long parentId, Long tabletId) {
        Long medicationId = tabletRepository.findMedicationIdOwnedBy(tabletId, parentId)
                .orElseThrow(() -> ApiException.notFound("Tablet not found"));
        MedicationInventory inventory = lockInventory(medicationId);
        MedicationTablet tablet = tabletRepository.findByIdForUpdate(tabletId)
                .orElseThrow(() -> ApiException.notFound("Tablet not found"));

        if (!tablet.isTaken()) {
            throw ApiException.conflict("This tablet is not marked as taken");   // so stock is never restored twice
        }

        inventory.restore(BigDecimal.ONE);
        tablet.undo();
        inventoryRepository.save(inventory);
        tabletRepository.save(tablet);

        return buildStrips(List.of(tablet.getMedication())).get(0);
    }

    // ================= used by MedicationService / InventoryService =================

    /** A new medicine starts with one AVAILABLE compartment per tablet. Runs inside the caller's transaction. */
    public void createStrip(Medication medication, BigDecimal quantity) {
        int count = toWholeTablets(quantity);
        List<MedicationTablet> tablets = new ArrayList<>(count);
        for (int slot = 1; slot <= count; slot++) {
            tablets.add(new MedicationTablet(medication, slot));
        }
        tabletRepository.saveAll(tablets);
    }

    /**
     * "Tablets now in the box = X". Adds empty compartments (refill) or removes AVAILABLE ones (fewer in the box).
     * Compartments that were already TAKEN are never removed, so history stays. Caller must hold the inventory lock.
     */
    public void setStock(Medication medication, MedicationInventory inventory, BigDecimal requested) {
        int target = toWholeTablets(requested);
        Long medicationId = medication.getId();
        int available = (int) tabletRepository.countByMedicationIdAndStatus(medicationId, TabletStatus.AVAILABLE);
        int total = (int) tabletRepository.countByMedicationId(medicationId);

        if (target > available) {
            int add = target - available;
            if (total + add > MAX_TABLETS) {
                throw ApiException.badRequest("A strip can hold at most " + MAX_TABLETS
                        + " compartments (including tablets already taken)");
            }
            int nextSlot = tabletRepository.findMaxSlotNumber(medicationId) + 1;
            List<MedicationTablet> added = new ArrayList<>(add);
            for (int i = 0; i < add; i++) {
                added.add(new MedicationTablet(medication, nextSlot + i));
            }
            tabletRepository.saveAll(added);
            total += add;
        } else if (target < available) {
            int remove = available - target;
            List<MedicationTablet> free = tabletRepository
                    .findByMedicationIdAndStatusOrderBySlotNumberDesc(medicationId, TabletStatus.AVAILABLE);
            tabletRepository.deleteAll(free.subList(0, remove));     // highest-numbered empty compartments first
            total -= remove;
        }
        inventory.syncCounts(BigDecimal.valueOf(target), BigDecimal.valueOf(total));
    }

    /** Tablet/capsule counts must be whole numbers between 0 and 200. */
    public static int toWholeTablets(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest("Tablet and capsule counts must be whole numbers");
        }
        if (quantity.compareTo(BigDecimal.valueOf(MAX_TABLETS)) > 0) {
            throw ApiException.badRequest("A strip can hold at most " + MAX_TABLETS + " tablets");
        }
        return quantity.intValueExact();
    }

    // ================= helpers =================

    private MedicationInventory lockInventory(Long medicationId) {
        return inventoryRepository.findByMedicationIdForUpdate(medicationId)
                .orElseThrow(() -> ApiException.notFound("Inventory not found"));
    }

    private Long resolveOwner(Long userId, Role role, Long parentId) {
        if (role == Role.PARENT) {
            return userId;                        // a parent only ever sees their own strips
        }
        if (parentId == null) {
            throw ApiException.badRequest("parentId is required");
        }
        familyService.assertChildCanAccessParent(userId, parentId);
        return parentId;
    }

    /** Loads everything for all strips in 4 queries, then assembles the responses in memory. */
    private List<StripResponse> buildStrips(List<Medication> medications) {
        if (medications.isEmpty()) return List.of();
        List<Long> ids = medications.stream().map(Medication::getId).toList();

        Map<Long, List<MedicationTablet>> tabletsByMedication =
                tabletRepository.findByMedicationIdInOrderBySlotNumber(ids).stream()
                        .collect(Collectors.groupingBy(t -> t.getMedication().getId()));
        Map<Long, MedicationInventory> inventoryByMedication =
                inventoryRepository.findByMedicationIdIn(ids).stream()
                        .collect(Collectors.toMap(i -> i.getMedication().getId(), Function.identity()));
        Map<Long, List<MedicationSchedule>> schedulesByMedication =
                scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(ids).stream()
                        .collect(Collectors.groupingBy(s -> s.getMedication().getId()));
        LocalDate today = LocalDate.now(clock);

        return medications.stream()
                .map(m -> toStrip(m,
                        tabletsByMedication.getOrDefault(m.getId(), List.of()),
                        inventoryByMedication.get(m.getId()),
                        schedulesByMedication.getOrDefault(m.getId(), List.of()),
                        today))
                .toList();
    }

    private StripResponse toStrip(Medication m, List<MedicationTablet> tablets, MedicationInventory inventory,
                                  List<MedicationSchedule> schedules, LocalDate today) {
        int taken = (int) tablets.stream().filter(MedicationTablet::isTaken).count();
        int takenToday = (int) tablets.stream()
                .filter(t -> t.isTaken() && t.getTakenAt() != null && t.getTakenAt().toLocalDate().equals(today))
                .count();
        int expectedToday = m.getDosagePerDose().multiply(BigDecimal.valueOf(schedules.size()))
                .setScale(0, RoundingMode.CEILING).intValue();
        int remaining = inventory == null ? tablets.size() - taken : inventory.getCurrentQuantity().intValue();

        return new StripResponse(m.getId(), m.getName(), m.getStrength(), m.getType(), m.getQuantityUnit(),
                m.getDosagePerDose(), tablets.size(), taken, remaining,
                inventory == null ? BigDecimal.ZERO : inventory.getLowStockThreshold(),
                inventory != null && inventory.isLowStock(),
                schedules.stream().map(MedicationSchedule::getDoseTime).toList(),
                expectedToday, takenToday,
                tablets.stream().map(TabletResponse::from).toList());
    }
}
