package com.careconnect.service;

import com.careconnect.dto.DoseResponse;
import com.careconnect.dto.SummaryResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationLogRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationLogServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 0);

    @Mock private MedicationRepository medicationRepository;
    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private MedicationInventoryRepository inventoryRepository;
    @Mock private MedicationLogRepository logRepository;
    @Mock private FamilyService familyService;

    private MedicationLogService service;
    private User parent;                  // id 1
    private Medication med;               // id 10, 1 tablet per dose, started 2026-10-01
    private MedicationSchedule morning;   // id 100, 08:00
    private MedicationSchedule evening;   // id 101, 20:00
    private MedicationInventory inventory; // 20 tablets

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneId.of("UTC"));
        service = new MedicationLogService(medicationRepository, scheduleRepository, inventoryRepository,
                logRepository, familyService, clock);
        parent = new User("Asha", "asha@test.com", "hash", Role.PARENT);
        ReflectionTestUtils.setField(parent, "id", 1L);
        med = medication(10L, "Paracetamol", LocalDate.of(2026, 10, 1), null);
        morning = schedule(med, 100L, 8, 0);
        evening = schedule(med, 101L, 20, 0);
        inventory = new MedicationInventory(med, bd("20"), bd("5"));
    }

    // ================= today's doses =================

    @Test
    void parentSeesTodaysDosesSortedByTimeAndAllPending() {
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(any())).thenReturn(List.of(evening, morning));
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));

        List<DoseResponse> doses = service.doses(1L, Role.PARENT, null, null);

        assertEquals(2, doses.size());
        assertEquals(LocalTime.of(8, 0), doses.get(0).scheduledTime());
        assertEquals(LocalTime.of(20, 0), doses.get(1).scheduledTime());
        assertTrue(doses.stream().allMatch(d -> d.status() == DoseStatus.PENDING));
        assertTrue(doses.stream().allMatch(d -> d.logId() == null && d.takenAt() == null));
        assertEquals("Paracetamol", doses.get(0).medicationName());
        assertEquals(0, bd("20").compareTo(doses.get(0).remainingQuantity()));
        assertEquals(TODAY, doses.get(0).scheduledDate());
    }

    @Test
    void confirmedDoseShowsAsTakenWithItsTimestamp() {
        MedicationLog log = takenLog(500L, morning, TODAY);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(any())).thenReturn(List.of(morning, evening));
        when(logRepository.findByParentAndDateAndStatus(1L, TODAY, LogStatus.TAKEN)).thenReturn(List.of(log));
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));

        List<DoseResponse> doses = service.doses(1L, Role.PARENT, null, null);

        assertEquals(DoseStatus.TAKEN, doses.get(0).status());
        assertEquals(500L, doses.get(0).logId());
        assertEquals(NOW, doses.get(0).takenAt());
        assertEquals(DoseStatus.PENDING, doses.get(1).status());   // unconfirmed stays pending, never "missed"
    }

    @Test
    void medicationThatEndedBeforeTheDateGeneratesNoDoses() {
        Medication ended = medication(11L, "Old course", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(ended));

        List<DoseResponse> doses = service.doses(1L, Role.PARENT, null, null);

        assertTrue(doses.isEmpty());
        verify(scheduleRepository, never()).findByMedicationIdInAndActiveTrueOrderByDoseTime(any());
    }

    @Test
    void onlyActiveSchedulesAreRequestedWhenGeneratingDoses() {
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));

        service.doses(1L, Role.PARENT, null, null);

        verify(scheduleRepository).findByMedicationIdInAndActiveTrueOrderByDoseTime(any());
    }

    @Test
    void historicalConfirmedDoseStaysVisibleAfterItsScheduleIsDeactivated() {
        morning.deactivate();                              // the repository no longer returns it
        LocalDate lastWeek = TODAY.minusDays(2);
        MedicationLog log = takenLog(500L, morning, lastWeek);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(logRepository.findByParentAndDateAndStatus(1L, lastWeek, LogStatus.TAKEN)).thenReturn(List.of(log));
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));

        List<DoseResponse> doses = service.doses(1L, Role.PARENT, null, lastWeek);

        assertEquals(1, doses.size());
        assertEquals(DoseStatus.TAKEN, doses.get(0).status());
        assertEquals(LocalTime.of(8, 0), doses.get(0).scheduledTime());
    }

    @Test
    void historicalConfirmedDoseStaysVisibleAfterItsMedicationIsDeactivated() {
        med.deactivate();                                  // inactive medications are not returned by the active query
        MedicationLog log = takenLog(500L, morning, TODAY);
        when(logRepository.findByParentAndDateAndStatus(1L, TODAY, LogStatus.TAKEN)).thenReturn(List.of(log));
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));

        List<DoseResponse> doses = service.doses(1L, Role.PARENT, null, null);

        assertEquals(1, doses.size());
        assertEquals(DoseStatus.TAKEN, doses.get(0).status());
    }

    @Test
    void futureDatesCannotBeViewed() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.doses(1L, Role.PARENT, null, TODAY.plusDays(1)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ================= mark as taken =================

    @Test
    void parentCanMarkADoseAsTakenAndStockGoesDownByTheDosage() {
        stubForConfirm(Optional.empty());

        DoseResponse response = service.markTaken(1L, 100L, null);

        assertEquals(DoseStatus.TAKEN, response.status());
        assertEquals(NOW, response.takenAt());
        assertEquals(0, bd("19").compareTo(response.remainingQuantity()));
        assertEquals(0, bd("19").compareTo(inventory.getCurrentQuantity()));

        ArgumentCaptor<MedicationLog> saved = ArgumentCaptor.forClass(MedicationLog.class);
        verify(logRepository).save(saved.capture());
        assertEquals(LogStatus.TAKEN, saved.getValue().getStatus());
        assertEquals(0, bd("1").compareTo(saved.getValue().getQuantityDeducted()));
        assertEquals(TODAY, saved.getValue().getScheduledDate());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void doseCanBeConfirmedForAPastDay() {
        LocalDate yesterday = TODAY.minusDays(1);
        when(scheduleRepository.findActiveOwned(100L, 1L)).thenReturn(Optional.of(morning));
        when(inventoryRepository.findByMedicationIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(logRepository.findForUpdate(100L, yesterday)).thenReturn(Optional.empty());

        DoseResponse response = service.markTaken(1L, 100L, yesterday);

        assertEquals(yesterday, response.scheduledDate());
    }

    @Test
    void duplicateConfirmationIsRejectedAndNothingChanges() {
        stubForConfirm(Optional.of(takenLog(500L, morning, TODAY)));

        ApiException ex = assertThrows(ApiException.class, () -> service.markTaken(1L, 100L, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, bd("20").compareTo(inventory.getCurrentQuantity()));   // not deducted a second time
        verify(logRepository, never()).save(any());                            // no partial data
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void confirmingAgainAfterUndoReusesTheLogRowAndDeductsAgain() {
        MedicationLog undone = takenLog(500L, morning, TODAY);
        undone.markUndone(NOW.minusHours(1));
        stubForConfirm(Optional.of(undone));

        DoseResponse response = service.markTaken(1L, 100L, null);

        assertEquals(DoseStatus.TAKEN, response.status());
        assertEquals(LogStatus.TAKEN, undone.getStatus());
        assertNull(undone.getUndoneAt());
        assertEquals(0, bd("19").compareTo(inventory.getCurrentQuantity()));
        verify(logRepository).save(undone);                                    // same row, no second row
    }

    @Test
    void insufficientStockRefusesConfirmationAndSavesNothing() {
        inventory = new MedicationInventory(med, bd("0.5"), bd("0"));          // dose needs 1
        stubForConfirm(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.markTaken(1L, 100L, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Not enough stock"));
        assertEquals(0, bd("0.5").compareTo(inventory.getCurrentQuantity()));  // never negative, never changed
        verify(logRepository, never()).save(any());                            // no log without a deduction
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void lastTabletCanBeTakenAndStockBecomesExactlyZero() {
        inventory = new MedicationInventory(med, bd("1"), bd("0"));
        stubForConfirm(Optional.empty());

        DoseResponse response = service.markTaken(1L, 100L, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.remainingQuantity()));
        assertTrue(response.lowStock());
    }

    @Test
    void childCannotMarkADoseAsTaken() {
        // a child owns no schedules, so the ownership query finds nothing
        when(scheduleRepository.findActiveOwned(100L, 2L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.markTaken(2L, 100L, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(inventoryRepository, logRepository);
    }

    @Test
    void anotherParentCannotConfirmThisParentsDose() {
        when(scheduleRepository.findActiveOwned(100L, 99L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.markTaken(99L, 100L, null));
        verifyNoInteractions(inventoryRepository, logRepository);
    }

    @Test
    void deactivatedScheduleCannotBeConfirmed() {
        when(scheduleRepository.findActiveOwned(100L, 1L)).thenReturn(Optional.empty());   // query only returns active ones

        ApiException ex = assertThrows(ApiException.class, () -> service.markTaken(1L, 100L, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(inventoryRepository, logRepository);
    }

    @Test
    void futureDoseCannotBeConfirmed() {
        ApiException ex = assertThrows(ApiException.class, () -> service.markTaken(1L, 100L, TODAY.plusDays(1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(scheduleRepository, inventoryRepository, logRepository);
    }

    @Test
    void doseBeforeTheMedicationStartedCannotBeConfirmed() {
        when(scheduleRepository.findActiveOwned(100L, 1L)).thenReturn(Optional.of(morning));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.markTaken(1L, 100L, LocalDate.of(2026, 9, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(inventoryRepository, logRepository);
    }

    // ================= undo =================

    @Test
    void parentCanUndoAndStockIsRestoredExactly() {
        inventory.deduct(bd("1"));                                              // 19 after the dose was taken
        MedicationLog log = takenLog(500L, morning, TODAY);
        stubForUndo(log);

        DoseResponse response = service.undo(1L, 500L);

        assertEquals(DoseStatus.PENDING, response.status());                    // pending again, not "missed"
        assertNull(response.takenAt());
        assertEquals(LogStatus.UNDONE, log.getStatus());                        // row kept as history
        assertNotNull(log.getUndoneAt());
        assertEquals(0, bd("20").compareTo(inventory.getCurrentQuantity()));
        assertEquals(0, bd("20").compareTo(response.remainingQuantity()));
    }

    @Test
    void undoRestoresWhatWasDeductedEvenIfTheDosageWasEditedLater() {
        inventory.deduct(bd("1"));
        MedicationLog log = takenLog(500L, morning, TODAY);                     // deducted 1
        med.updateDetails("Paracetamol", MedicationType.SYRUP, null, bd("2"), "Twice daily",
                FoodInstruction.ANY_TIME, null, LocalDate.of(2026, 10, 1), null);   // dosage now 2
        stubForUndo(log);

        service.undo(1L, 500L);

        assertEquals(0, bd("20").compareTo(inventory.getCurrentQuantity()));    // +1 (stored), not +2
    }

    @Test
    void undoCannotRestoreStockTwice() {
        inventory.deduct(bd("1"));
        MedicationLog log = takenLog(500L, morning, TODAY);
        stubForUndo(log);

        service.undo(1L, 500L);                                                 // first undo: 19 -> 20
        ApiException ex = assertThrows(ApiException.class, () -> service.undo(1L, 500L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, bd("20").compareTo(inventory.getCurrentQuantity()));    // still 20, not 21
    }

    @Test
    void childCannotUndoAConfirmation() {
        when(logRepository.findMedicationIdOwnedBy(500L, 2L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.undo(2L, 500L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void parentCannotUndoAnotherParentsLog() {
        when(logRepository.findMedicationIdOwnedBy(500L, 99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.undo(99L, 500L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(logRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(inventoryRepository);
    }

    // ================= who may read =================

    @Test
    void parentAlwaysReadsOwnDataEvenIfAnotherParentIdIsSupplied() {
        service.doses(99L, Role.PARENT, 1L, null);                              // parentId from the client is ignored

        verify(medicationRepository).findByParentIdAndActiveTrueOrderByCreatedAtDesc(99L);
        verify(medicationRepository, never()).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);
    }

    @Test
    void childWithApprovedConnectionCanReadParentsDoses() {
        service.doses(2L, Role.CHILD, 1L, null);

        verify(familyService).assertChildCanAccessParent(2L, 1L);
        verify(medicationRepository).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);
    }

    @Test
    void childWithoutApprovedConnectionCannotReadDosesOrSummary() {
        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanAccessParent(2L, 1L);

        ApiException doses = assertThrows(ApiException.class, () -> service.doses(2L, Role.CHILD, 1L, null));
        ApiException summary = assertThrows(ApiException.class, () -> service.summary(2L, Role.CHILD, 1L, null));

        assertEquals(HttpStatus.FORBIDDEN, doses.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, summary.getStatus());
        verifyNoInteractions(medicationRepository, logRepository);
    }

    @Test
    void childMustSayWhichParent() {
        ApiException ex = assertThrows(ApiException.class, () -> service.doses(2L, Role.CHILD, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ================= summary =================

    @Test
    void summaryCountsTakenAndPendingAndPercentage() {
        MedicationSchedule s12 = schedule(med, 102L, 12, 0);
        MedicationSchedule s16 = schedule(med, 103L, 16, 0);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(any()))
                .thenReturn(List.of(morning, s12, s16, evening));
        when(logRepository.findByParentAndDateAndStatus(1L, TODAY, LogStatus.TAKEN))
                .thenReturn(List.of(takenLog(500L, morning, TODAY), takenLog(501L, s16, TODAY)));
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));

        SummaryResponse summary = service.summary(1L, Role.PARENT, null, null);

        assertEquals(4, summary.totalDoses());
        assertEquals(2, summary.takenDoses());
        assertEquals(2, summary.pendingDoses());
        assertEquals(50, summary.completionPercent());
        assertEquals(TODAY, summary.date());
    }

    @Test
    void summaryRoundsPercentageAndHandlesNoDoses() {
        SummaryResponse none = service.summary(1L, Role.PARENT, null, null);
        assertEquals(0, none.totalDoses());
        assertEquals(0, none.completionPercent());                              // no division by zero

        MedicationSchedule s12 = schedule(med, 102L, 12, 0);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(any()))
                .thenReturn(List.of(morning, s12, evening));
        when(logRepository.findByParentAndDateAndStatus(1L, TODAY, LogStatus.TAKEN))
                .thenReturn(List.of(takenLog(500L, morning, TODAY)));
        when(inventoryRepository.findByMedicationIdIn(any())).thenReturn(List.of(inventory));

        SummaryResponse oneOfThree = service.summary(1L, Role.PARENT, null, null);
        assertEquals(33, oneOfThree.completionPercent());
        assertEquals(2, oneOfThree.pendingDoses());
    }

    // ================= tablets and capsules use the strip, not dose logs =================

    @Test
    void tabletMedicinesAreNotListedAsDoses() {
        Medication tablet = medicationOfType(12L, MedicationType.TABLET);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(tablet));

        List<DoseResponse> doses = service.doses(1L, Role.PARENT, null, null);

        assertTrue(doses.isEmpty());
        verify(scheduleRepository, never()).findByMedicationIdInAndActiveTrueOrderByDoseTime(any());
    }

    @Test
    void tabletDoseCannotBeConfirmedThroughTheDoseEndpoint() {
        Medication tablet = medicationOfType(12L, MedicationType.CAPSULE);
        MedicationSchedule s = schedule(tablet, 150L, 8, 0);
        when(scheduleRepository.findActiveOwned(150L, 1L)).thenReturn(Optional.of(s));

        ApiException ex = assertThrows(ApiException.class, () -> service.markTaken(1L, 150L, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(inventoryRepository, logRepository);   // stock untouched
    }

    @Test
    void oldTabletLogCannotBeUndoneBecauseItWouldChangeStockWithoutACompartment() {
        Medication tablet = medicationOfType(12L, MedicationType.TABLET);
        MedicationLog oldLog = takenLog(600L, schedule(tablet, 150L, 8, 0), TODAY);
        MedicationInventory tabletStock = new MedicationInventory(tablet, bd("10"), bd("2"));
        when(logRepository.findMedicationIdOwnedBy(600L, 1L)).thenReturn(Optional.of(12L));
        when(inventoryRepository.findByMedicationIdForUpdate(12L)).thenReturn(Optional.of(tabletStock));
        when(logRepository.findByIdForUpdate(600L)).thenReturn(Optional.of(oldLog));

        ApiException ex = assertThrows(ApiException.class, () -> service.undo(1L, 600L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(0, bd("10").compareTo(tabletStock.getCurrentQuantity()));
        verify(inventoryRepository, never()).save(any());
    }

    // ================= helpers =================

    private Medication medicationOfType(long id, MedicationType type) {
        Medication m = new Medication(parent, "Tablet medicine", type, null, bd("1"), "Daily",
                FoodInstruction.ANY_TIME, null, LocalDate.of(2026, 10, 1), null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }


    private void stubForConfirm(Optional<MedicationLog> existing) {
        when(scheduleRepository.findActiveOwned(100L, 1L)).thenReturn(Optional.of(morning));
        when(inventoryRepository.findByMedicationIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(logRepository.findForUpdate(100L, TODAY)).thenReturn(existing);
    }

    private void stubForUndo(MedicationLog log) {
        when(logRepository.findMedicationIdOwnedBy(500L, 1L)).thenReturn(Optional.of(10L));
        when(inventoryRepository.findByMedicationIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(logRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(log));
    }

    private Medication medication(long id, String name, LocalDate start, LocalDate end) {
        Medication m = new Medication(parent, name, MedicationType.SYRUP, "500 mg", bd("1"), "Twice daily",
                FoodInstruction.ANY_TIME, null, start, end);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private MedicationSchedule schedule(Medication m, long id, int hour, int minute) {
        MedicationSchedule s = new MedicationSchedule(m, LocalTime.of(hour, minute));
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private MedicationLog takenLog(long id, MedicationSchedule s, LocalDate date) {
        MedicationLog log = new MedicationLog(s.getMedication(), s, date, bd("1"), NOW);
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
