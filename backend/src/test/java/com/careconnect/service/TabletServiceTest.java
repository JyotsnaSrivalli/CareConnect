package com.careconnect.service;

import com.careconnect.dto.StripResponse;
import com.careconnect.dto.TabletResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import com.careconnect.repository.MedicationTabletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TabletServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 8, 7);

    @Mock private MedicationRepository medicationRepository;
    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private MedicationInventoryRepository inventoryRepository;
    @Mock private MedicationTabletRepository tabletRepository;
    @Mock private FamilyService familyService;

    private TabletService service;
    private User parent;                       // id 1
    private Medication med;                    // id 10, Paracetamol, 1 tablet per dose, twice daily
    private MedicationInventory inventory;     // 12 tablets, low at <= 3
    private List<MedicationTablet> strip;      // 12 compartments, tablet id == slot number

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T08:07:00Z"), ZoneId.of("UTC"));
        service = new TabletService(medicationRepository, scheduleRepository, inventoryRepository,
                tabletRepository, familyService, clock);
        parent = new User("Asha", "asha@test.com", "hash", Role.PARENT);
        ReflectionTestUtils.setField(parent, "id", 1L);
        med = new Medication(parent, "Paracetamol", MedicationType.TABLET, "500 mg", bd("1"), "Twice daily",
                FoodInstruction.AFTER_FOOD, null, LocalDate.of(2026, 10, 1), null);
        ReflectionTestUtils.setField(med, "id", 10L);
        inventory = new MedicationInventory(med, bd("12"), bd("3"));
        strip = new ArrayList<>();
        for (int slot = 1; slot <= 12; slot++) {
            MedicationTablet t = new MedicationTablet(med, slot);
            ReflectionTestUtils.setField(t, "id", (long) slot);
            strip.add(t);
        }
    }

    // ================= reading =================

    @Test
    void parentSeesOneStripWithTwelveCompartments() {
        stubStripReads();

        List<StripResponse> strips = service.strips(1L, Role.PARENT, null);

        assertEquals(1, strips.size());
        StripResponse s = strips.get(0);
        assertEquals("Paracetamol", s.medicationName());
        assertEquals(12, s.totalTablets());
        assertEquals(12, s.tablets().size());
        assertEquals(0, s.takenTablets());
        assertEquals(12, s.remainingTablets());
        assertTrue(s.tablets().stream().allMatch(t -> t.status() == TabletStatus.AVAILABLE && t.takenAt() == null));
        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)), s.scheduleTimes());
        assertEquals(2, s.expectedToday());          // 1 tablet x 2 dose times
        assertEquals(0, s.takenToday());
        assertFalse(s.lowStock());
    }

    @Test
    void syrupMedicinesAreNotShownAsStrips() {
        Medication syrup = new Medication(parent, "Cough syrup", MedicationType.SYRUP, null, bd("5"), "Daily",
                FoodInstruction.ANY_TIME, null, LocalDate.of(2026, 10, 1), null);
        ReflectionTestUtils.setField(syrup, "id", 11L);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(syrup));

        assertTrue(service.strips(1L, Role.PARENT, null).isEmpty());
        verifyNoInteractions(tabletRepository);
    }

    @Test
    void parentAlwaysReadsOwnStripsEvenIfAnotherParentIdIsSupplied() {
        service.strips(99L, Role.PARENT, 1L);

        verify(medicationRepository).findByParentIdAndActiveTrueOrderByCreatedAtDesc(99L);
        verify(medicationRepository, never()).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);
    }

    @Test
    void approvedChildCanReadStripsButUnapprovedChildCannot() {
        service.strips(2L, Role.CHILD, 1L);
        verify(familyService).assertChildCanAccessParent(2L, 1L);
        verify(medicationRepository).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);

        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanAccessParent(3L, 1L);
        ApiException ex = assertThrows(ApiException.class, () -> service.strips(3L, Role.CHILD, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(medicationRepository, times(1)).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);   // not called again
    }

    @Test
    void childMustSayWhichParent() {
        ApiException ex = assertThrows(ApiException.class, () -> service.strips(2L, Role.CHILD, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ================= taking tablets =================

    @Test
    void takingOneCompartmentMarksItTakenWithTimeAndDropsStockFromTwelveToEleven() {
        stubTake(5L);
        stubStripReads();

        StripResponse result = service.take(1L, 5L);

        MedicationTablet tablet5 = strip.get(4);
        assertEquals(TabletStatus.TAKEN, tablet5.getStatus());
        assertEquals(NOW, tablet5.getTakenAt());                                  // date + time recorded
        assertEquals(0, bd("11").compareTo(inventory.getCurrentQuantity()));
        assertEquals(11, result.remainingTablets());
        assertEquals(1, result.takenTablets());
        assertEquals(1, result.takenToday());
        assertEquals(12, result.totalTablets());
        TabletResponse r5 = result.tablets().get(4);
        assertEquals(TabletStatus.TAKEN, r5.status());
        assertEquals(5, r5.slotNumber());
        verify(inventoryRepository).save(inventory);
        verify(tabletRepository).save(tablet5);
    }

    @Test
    void morningAndEveningTabletsUseTwoDifferentCompartmentsOnTheSameStrip() {
        stubTake(5L);
        stubTake(6L);
        stubStripReads();

        service.take(1L, 5L);                                                      // morning: 12 -> 11
        assertEquals(0, bd("11").compareTo(inventory.getCurrentQuantity()));
        StripResponse result = service.take(1L, 6L);                               // evening: 11 -> 10

        assertEquals(0, bd("10").compareTo(inventory.getCurrentQuantity()));
        assertEquals(10, result.remainingTablets());
        assertEquals(2, result.takenTablets());
        assertEquals(2, result.takenToday());
        assertEquals(12, result.tablets().size());                                 // still ONE strip of 12
    }

    @Test
    void theSameCompartmentCannotBeUsedTwiceAndStockIsNotDeductedAgain() {
        stubTake(5L);
        stubStripReads();
        service.take(1L, 5L);

        ApiException ex = assertThrows(ApiException.class, () -> service.take(1L, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, bd("11").compareTo(inventory.getCurrentQuantity()));       // deducted once
        verify(tabletRepository, times(1)).save(any());
    }

    @Test
    void takingWithNoStockLeftIsRefusedAndNothingChanges() {
        inventory = new MedicationInventory(med, bd("12"), bd("3"));
        inventory.syncCounts(bd("0"), bd("12"));
        stubTake(5L);

        ApiException ex = assertThrows(ApiException.class, () -> service.take(1L, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(TabletStatus.AVAILABLE, strip.get(4).getStatus());
        assertEquals(0, bd("0").compareTo(inventory.getCurrentQuantity()));       // never negative
        verify(tabletRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void childCannotTakeATablet() {
        when(tabletRepository.findMedicationIdOwnedBy(5L, 2L)).thenReturn(Optional.empty());   // a child owns nothing

        ApiException ex = assertThrows(ApiException.class, () -> service.take(2L, 5L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void anotherParentCannotTakeOrUndoThisParentsTablet() {
        when(tabletRepository.findMedicationIdOwnedBy(5L, 99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.take(99L, 5L)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.undo(99L, 5L)).getStatus());
        verifyNoInteractions(inventoryRepository);
    }

    // ================= undo =================

    @Test
    void undoRestoresThatExactCompartmentAndOneUnitOfStock() {
        stubTake(5L);
        stubStripReads();
        service.take(1L, 5L);                                                      // 12 -> 11

        StripResponse result = service.undo(1L, 5L);

        MedicationTablet tablet5 = strip.get(4);
        assertEquals(TabletStatus.AVAILABLE, tablet5.getStatus());
        assertNull(tablet5.getTakenAt());
        assertEquals(5, tablet5.getSlotNumber());                                  // the same compartment
        assertEquals(0, bd("12").compareTo(inventory.getCurrentQuantity()));
        assertEquals(12, result.remainingTablets());
        assertEquals(0, result.takenTablets());
        assertEquals(TabletStatus.AVAILABLE, result.tablets().get(4).status());
    }

    @Test
    void undoCannotRestoreStockTwice() {
        stubTake(5L);
        stubStripReads();
        service.take(1L, 5L);
        service.undo(1L, 5L);                                                      // back to 12

        ApiException ex = assertThrows(ApiException.class, () -> service.undo(1L, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, bd("12").compareTo(inventory.getCurrentQuantity()));       // not 13
    }

    @Test
    void undoOfAnAvailableCompartmentIsRefused() {
        stubTake(7L);

        ApiException ex = assertThrows(ApiException.class, () -> service.undo(1L, 7L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, bd("12").compareTo(inventory.getCurrentQuantity()));
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void tabletsTakenOnAnEarlierDayDoNotCountAsTakenToday() {
        strip.get(0).take(LocalDateTime.of(2026, 11, 2, 20, 0));
        inventory.syncCounts(bd("11"), bd("12"));
        stubStripReads();

        StripResponse s = service.strips(1L, Role.PARENT, null).get(0);

        assertEquals(1, s.takenTablets());
        assertEquals(0, s.takenToday());
    }

    // ================= changing the stock resizes the strip =================

    @Test
    void refillAddsNewEmptyCompartmentsAfterTheLastSlot() {
        List<MedicationTablet> saved = new ArrayList<>();
        when(tabletRepository.countByMedicationIdAndStatus(10L, TabletStatus.AVAILABLE)).thenReturn(12L);
        when(tabletRepository.countByMedicationId(10L)).thenReturn(12L);
        when(tabletRepository.findMaxSlotNumber(10L)).thenReturn(12);
        when(tabletRepository.saveAll(any())).thenAnswer(call -> {
            Iterable<MedicationTablet> added = call.getArgument(0);
            added.forEach(saved::add);
            return saved;
        });

        service.setStock(med, inventory, bd("15"));

        assertEquals(List.of(13, 14, 15), saved.stream().map(MedicationTablet::getSlotNumber).toList());
        assertEquals(0, bd("15").compareTo(inventory.getCurrentQuantity()));
        assertEquals(0, bd("15").compareTo(inventory.getTotalQuantity()));
    }

    @Test
    void havingFewerInTheBoxRemovesOnlyAvailableCompartmentsHighestFirst() {
        for (int i = 0; i < 3; i++) strip.get(i).take(NOW);                        // slots 1-3 taken, 9 available
        List<MedicationTablet> available = new ArrayList<>(strip.subList(3, 12));
        java.util.Collections.reverse(available);                                  // 12, 11, ... 4
        List<MedicationTablet> deleted = new ArrayList<>();
        when(tabletRepository.countByMedicationIdAndStatus(10L, TabletStatus.AVAILABLE)).thenReturn(9L);
        when(tabletRepository.countByMedicationId(10L)).thenReturn(12L);
        when(tabletRepository.findByMedicationIdAndStatusOrderBySlotNumberDesc(10L, TabletStatus.AVAILABLE)).thenReturn(available);
        doAnswer(call -> {
            Iterable<MedicationTablet> gone = call.getArgument(0);
            gone.forEach(deleted::add);
            return null;
        }).when(tabletRepository).deleteAll(any());

        service.setStock(med, inventory, bd("5"));

        assertEquals(List.of(12, 11, 10, 9), deleted.stream().map(MedicationTablet::getSlotNumber).toList());
        assertEquals(0, bd("5").compareTo(inventory.getCurrentQuantity()));
        assertEquals(0, bd("8").compareTo(inventory.getTotalQuantity()));          // 3 taken + 5 available
    }

    @Test
    void stockMustBeAWholeNumberAndStripCannotExceedTwoHundred() {
        ApiException fraction = assertThrows(ApiException.class, () -> service.setStock(med, inventory, bd("2.5")));
        assertEquals(HttpStatus.BAD_REQUEST, fraction.getStatus());

        when(tabletRepository.countByMedicationIdAndStatus(10L, TabletStatus.AVAILABLE)).thenReturn(150L);
        when(tabletRepository.countByMedicationId(10L)).thenReturn(190L);
        ApiException tooMany = assertThrows(ApiException.class, () -> service.setStock(med, inventory, bd("161")));
        assertEquals(HttpStatus.BAD_REQUEST, tooMany.getStatus());                 // 190 + 10 > 200
        verify(tabletRepository, never()).saveAll(any());
    }

    @Test
    void newMedicineGetsOneCompartmentPerTablet() {
        List<MedicationTablet> saved = new ArrayList<>();
        when(tabletRepository.saveAll(any())).thenAnswer(call -> {
            Iterable<MedicationTablet> added = call.getArgument(0);
            added.forEach(saved::add);
            return saved;
        });

        service.createStrip(med, bd("12"));

        assertEquals(12, saved.size());
        assertEquals(1, saved.get(0).getSlotNumber());
        assertEquals(12, saved.get(11).getSlotNumber());
        assertTrue(saved.stream().allMatch(t -> t.getStatus() == TabletStatus.AVAILABLE));
    }

    @Test
    void wholeNumberRule() {
        assertEquals(20, TabletService.toWholeTablets(bd("20.00")));
        assertEquals(0, TabletService.toWholeTablets(bd("0")));
        assertThrows(ApiException.class, () -> TabletService.toWholeTablets(bd("0.5")));
        assertThrows(ApiException.class, () -> TabletService.toWholeTablets(bd("201")));
    }

    // ================= helpers =================

    private void stubTake(long tabletId) {
        when(tabletRepository.findMedicationIdOwnedBy(tabletId, 1L)).thenReturn(Optional.of(10L));
        when(inventoryRepository.findByMedicationIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(tabletRepository.findByIdForUpdate(tabletId)).thenReturn(Optional.of(strip.get((int) tabletId - 1)));
    }

    private void stubStripReads() {
        lenient().when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(tabletRepository.findByMedicationIdInOrderBySlotNumber(any())).thenReturn(strip);
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(any())).thenReturn(List.of(
                schedule(100L, 8, 0), schedule(101L, 20, 0)));
    }

    private MedicationSchedule schedule(long id, int hour, int minute) {
        MedicationSchedule s = new MedicationSchedule(med, LocalTime.of(hour, minute));
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
