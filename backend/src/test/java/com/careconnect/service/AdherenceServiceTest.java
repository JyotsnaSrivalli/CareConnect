package com.careconnect.service;

import com.careconnect.dto.AdherenceMedicationItem;
import com.careconnect.dto.AdherenceSummaryResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationLogRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import com.careconnect.repository.MedicationTabletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the adherence summary maths and the access rules. The repositories are mocks: the tests set up
 * "what the database would return" and check what the service makes of it.
 * (Lenient mode: the shared setup stubs every repository, and not every test needs every one of them.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdherenceServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final LocalDate PAST = TODAY.minusDays(2);

    @Mock private MedicationRepository medicationRepository;
    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private MedicationInventoryRepository inventoryRepository;
    @Mock private MedicationTabletRepository tabletRepository;
    @Mock private MedicationLogRepository logRepository;
    @Mock private FamilyService familyService;

    private AdherenceService service;
    private User parent;

    // the "database" for one test: each test fills these lists, then calls summaryOn()/summaryToday()
    private final List<Medication> medications = new ArrayList<>();
    private final List<MedicationSchedule> activeSchedules = new ArrayList<>();
    private final List<MedicationInventory> stock = new ArrayList<>();
    private final List<MedicationLog> takenLogs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("UTC"));
        service = new AdherenceService(medicationRepository, scheduleRepository, inventoryRepository,
                tabletRepository, logRepository, familyService, clock);
        parent = new User("Asha", "asha@test.com", "hash", Role.PARENT);
        ReflectionTestUtils.setField(parent, "id", 1L);
    }

    // ================= today: tablets and capsules =================

    @Test
    void tabletsTodayOneOfTwoConfirmedIsFiftyPercent() {
        Medication m = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 11);
        schedule(m, 100L, 8);
        schedule(m, 101L, 20);
        tabletsTaken(m, TODAY, 1);

        AdherenceSummaryResponse r = summaryToday();
        AdherenceMedicationItem item = r.medications().get(0);

        assertTrue(r.today());
        assertTrue(r.expectedAvailable());
        assertEquals(2, item.expected());
        assertEquals(1, item.confirmed());
        assertEquals(1, item.remaining());
        assertEquals(50, item.completionPercent());
        assertEquals("tablets", item.unit());
        assertEquals(2, r.expectedTotal());
        assertEquals(1, r.confirmedTotal());
        assertEquals(1, r.remainingTotal());
        assertEquals(50, r.completionPercent());
    }

    @Test
    void twoTabletsPerDoseTwiceADayExpectsFourTabletsNotTwoDoses() {
        Medication m = medication(10L, "Two at a time", MedicationType.TABLET, "2", 20);
        schedule(m, 100L, 8);
        schedule(m, 101L, 20);
        tabletsTaken(m, TODAY, 3);

        AdherenceMedicationItem item = summaryToday().medications().get(0);

        assertEquals(4, item.expected());       // a compartment is NOT a dose: 2 tablets x 2 times = 4 tablets
        assertEquals(3, item.confirmed());
        assertEquals(1, item.remaining());
        assertEquals(75, item.completionPercent());
        assertEquals(2, item.timesPerDay());
    }

    @Test
    void fractionalDosageIsRoundedUpLikeTheStripHeader() {
        Medication m = medication(10L, "Half", MedicationType.TABLET, "1.5", 20);
        schedule(m, 100L, 8);

        assertEquals(2, summaryToday().medications().get(0).expected());
    }

    @Test
    void capsulesAreLabelledAsCapsules() {
        Medication m = medication(10L, "Omega", MedicationType.CAPSULE, "1", 20);
        schedule(m, 100L, 8);
        tabletsTaken(m, TODAY, 1);

        assertEquals("capsules", summaryToday().medications().get(0).unit());
    }

    @Test
    void physicalStockIsReportedSeparatelyFromConfirmations() {
        Medication m = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 11);
        schedule(m, 100L, 8);
        tabletsTaken(m, TODAY, 1);

        AdherenceMedicationItem item = summaryToday().medications().get(0);

        assertEquals(1, item.confirmed());                                        // adherence
        assertEquals(new BigDecimal("11"), item.physicalStock().remaining());     // what is left in the box
    }

    // ================= today: syrup =================

    @Test
    void syrupCountsDosesFromActiveSchedulesAndTakenLogs() {
        Medication m = medication(20L, "Cough syrup", MedicationType.SYRUP, "5", 100);
        MedicationSchedule morning = schedule(m, 200L, 8);
        schedule(m, 201L, 20);
        takenLog(m, morning, TODAY);

        AdherenceMedicationItem item = summaryToday().medications().get(0);

        assertEquals("doses", item.unit());
        assertEquals(2, item.expected());
        assertEquals(1, item.confirmed());
        assertEquals(1, item.remaining());
        assertEquals(50, item.completionPercent());
        assertEquals(0, item.extraConfirmations());
    }

    @Test
    void takenLogOfARemovedScheduleIsConfirmedButDoesNotCompleteAnActiveDose() {
        Medication m = medication(20L, "Cough syrup", MedicationType.SYRUP, "5", 100);
        schedule(m, 200L, 8);                                       // still active, still pending
        MedicationSchedule removed = schedule(m, 201L, 20);
        activeSchedules.remove(removed);                            // removed later: no longer an active schedule
        takenLog(m, removed, TODAY);

        AdherenceMedicationItem item = summaryToday().medications().get(0);

        assertEquals(1, item.expected());
        assertEquals(1, item.confirmed());
        assertEquals(1, item.remaining());           // the active 8 am dose is still pending
        assertEquals(0, item.completionPercent());
        assertEquals(1, item.extraConfirmations());
    }

    // ================= no double counting =================

    @Test
    void tabletAndSyrupAreCountedOnceEachAndOldTabletLogsAreIgnored() {
        Medication tablet = medication(10L, "Tablet", MedicationType.TABLET, "1", 10);
        MedicationSchedule tabletSchedule = schedule(tablet, 100L, 8);
        tabletsTaken(tablet, TODAY, 1);
        takenLog(tablet, tabletSchedule, TODAY);      // an old-style log for a TABLET medicine: must be ignored
        Medication syrup = medication(20L, "Syrup", MedicationType.SYRUP, "5", 100);
        MedicationSchedule syrupSchedule = schedule(syrup, 200L, 8);
        takenLog(syrup, syrupSchedule, TODAY);

        AdherenceSummaryResponse r = summaryToday();

        assertEquals(2, r.confirmedTotal());          // not 3
        assertEquals(2, r.expectedTotal());
        assertEquals(100, r.completionPercent());
        assertEquals(0, r.remainingTotal());
    }

    // ================= extra confirmations =================

    @Test
    void extraTabletsNeverMakeRemainingNegativeOrPercentAbove100AndAreFlagged() {
        Medication m = medication(10L, "Many", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);
        schedule(m, 101L, 20);
        tabletsTaken(m, TODAY, 3);

        AdherenceSummaryResponse r = summaryToday();
        AdherenceMedicationItem item = r.medications().get(0);

        assertEquals(3, item.confirmed());            // the real number of clicks is still shown
        assertEquals(0, item.remaining());
        assertEquals(100, item.completionPercent());
        assertEquals(1, item.extraConfirmations());
        assertEquals(1, r.extraConfirmations());
    }

    @Test
    void extraTabletsOfOneMedicineDoNotHideAPendingDoseOfAnother() {
        Medication a = medication(10L, "A", MedicationType.TABLET, "1", 10);
        schedule(a, 100L, 8);
        schedule(a, 101L, 20);
        tabletsTaken(a, TODAY, 4);                    // 2 too many
        Medication b = medication(11L, "B", MedicationType.TABLET, "1", 10);
        schedule(b, 102L, 8);
        schedule(b, 103L, 20);                        // nothing confirmed

        AdherenceSummaryResponse r = summaryToday();

        assertEquals(4, r.expectedTotal());
        assertEquals(2, r.remainingTotal());          // B is still pending
        assertEquals(50, r.completionPercent());      // not 100
        assertEquals(2, r.extraConfirmations());
        assertEquals(4, r.confirmedTotal());
    }

    // ================= what is (not) shown today =================

    @Test
    void medicineOutsideItsStartAndEndDatesIsNotExpectedAndNotListed() {
        Medication notStarted = medication(10L, "Future", MedicationType.TABLET, "1", 10);
        ReflectionTestUtils.setField(notStarted, "startDate", TODAY.plusDays(3));
        schedule(notStarted, 100L, 8);
        Medication ended = medication(11L, "Ended", MedicationType.TABLET, "1", 10);
        ReflectionTestUtils.setField(ended, "endDate", TODAY.minusDays(1));
        schedule(ended, 101L, 8);

        AdherenceSummaryResponse r = summaryToday();

        assertTrue(r.medications().isEmpty());
        assertEquals(0, r.expectedTotal());
        assertNull(r.completionPercent());            // nothing to complete
    }

    @Test
    void noMedicinesGivesAnEmptySummary() {
        AdherenceSummaryResponse r = summaryToday();

        assertTrue(r.medications().isEmpty());
        assertEquals(0, r.confirmedTotal());
        assertNull(r.completionPercent());
        assertTrue(r.today());
    }

    @Test
    void removedMedicineIsNotShown() {
        Medication m = medication(10L, "Gone", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);
        tabletsTaken(m, TODAY, 1);
        ReflectionTestUtils.setField(m, "active", false);

        assertTrue(summaryToday().medications().isEmpty());
    }

    // ================= previous dates =================

    @Test
    void pastDateShowsConfirmedCountsOnlyAndExplainsWhy() {
        Medication tablet = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 10);
        schedule(tablet, 100L, 8);
        schedule(tablet, 101L, 20);
        tabletsTaken(tablet, PAST, 2);
        tabletsTaken(tablet, TODAY, 1);               // taken today: must NOT appear on the past day
        Medication syrup = medication(20L, "Syrup", MedicationType.SYRUP, "5", 100);
        MedicationSchedule syrupSchedule = schedule(syrup, 200L, 8);
        takenLog(syrup, syrupSchedule, PAST);
        Medication idle = medication(30L, "Idle", MedicationType.TABLET, "1", 10);
        schedule(idle, 300L, 8);

        AdherenceSummaryResponse r = summaryOn(PAST);

        assertFalse(r.today());
        assertFalse(r.expectedAvailable());
        assertNull(r.expectedTotal());
        assertNull(r.remainingTotal());
        assertNull(r.completionPercent());
        assertNull(r.extraConfirmations());
        assertEquals(3, r.confirmedTotal());          // 2 tablets + 1 syrup dose confirmed on that date
        assertEquals(2, r.medications().size());      // "Idle" had no confirmations that day, so it is not listed
        AdherenceMedicationItem paracetamol = r.medications().stream()
                .filter(i -> i.name().equals("Paracetamol")).findFirst().orElseThrow();
        assertEquals(2, paracetamol.confirmed());
        assertNull(paracetamol.expected());
        assertNull(paracetamol.completionPercent());
        assertNotNull(r.message());
        assertTrue(r.message().contains("past schedules"));
    }

    @Test
    void pastDateNeverLooksAtTodaysSchedules() {
        Medication m = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);
        tabletsTaken(m, PAST, 1);

        summaryOn(PAST);

        verify(scheduleRepository, never()).findByMedicationIdInAndActiveTrueOrderByDoseTime(any());
    }

    @Test
    void pastDateWithNothingConfirmedIsEmptyNotZeroPercent() {
        Medication m = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);

        AdherenceSummaryResponse r = summaryOn(PAST);

        assertEquals(0, r.confirmedTotal());
        assertTrue(r.medications().isEmpty());
        assertNull(r.completionPercent());
    }

    @Test
    void futureDateIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.summary(1L, Role.PARENT, null, TODAY.plusDays(1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(medicationRepository);
    }

    @Test
    void explicitDateEqualToTodayGivesTheFullSummary() {
        Medication m = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);

        assertTrue(summaryOn(TODAY).expectedAvailable());
    }

    // ================= ownership and child access =================

    @Test
    void parentAlwaysGetsTheirOwnDataEvenIfTheyPassAnotherParentsId() {
        Medication m = medication(10L, "Mine", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);
        arrange();

        AdherenceSummaryResponse r = service.summary(1L, Role.PARENT, 99L, null);

        assertEquals(1, r.medications().size());
        verify(medicationRepository).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);
        verify(medicationRepository, never()).findByParentIdAndActiveTrueOrderByCreatedAtDesc(99L);
        verifyNoInteractions(familyService);
    }

    @Test
    void approvedChildSeesTheParentsSummary() {
        Medication m = medication(10L, "Mums tablet", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);
        tabletsTaken(m, TODAY, 1);
        arrange();

        AdherenceSummaryResponse r = service.summary(2L, Role.CHILD, 1L, null);

        assertEquals(1, r.confirmedTotal());
        verify(familyService).assertChildCanAccessParent(2L, 1L);
        verify(medicationRepository).findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L);
    }

    @Test
    void childWithoutAnApprovedConnectionIsForbiddenAndNoDataIsRead() {
        // FamilyService throws this for: no connection, PENDING, REJECTED and revoked connections
        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanAccessParent(2L, 1L);

        ApiException ex = assertThrows(ApiException.class, () -> service.summary(2L, Role.CHILD, 1L, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(medicationRepository, scheduleRepository, inventoryRepository, tabletRepository, logRepository);
    }

    @Test
    void childMustSayWhichParent() {
        ApiException ex = assertThrows(ApiException.class, () -> service.summary(2L, Role.CHILD, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(medicationRepository);
    }

    // ================= read-only =================

    @Test
    void viewingTheSummaryNeverWritesAnything() {
        Medication m = medication(10L, "Paracetamol", MedicationType.TABLET, "1", 10);
        schedule(m, 100L, 8);
        tabletsTaken(m, TODAY, 1);
        Medication s = medication(20L, "Syrup", MedicationType.SYRUP, "5", 100);
        takenLog(s, schedule(s, 200L, 8), TODAY);

        summaryToday();
        summaryOn(PAST);

        verify(tabletRepository, never()).save(any());
        verify(tabletRepository, never()).saveAll(any());
        verify(inventoryRepository, never()).save(any());
        verify(logRepository, never()).save(any());
        verify(medicationRepository, never()).save(any());
        assertEquals(new BigDecimal("10"), stock.get(0).getCurrentQuantity());   // stock unchanged by looking
    }

    // ================= helpers =================

    private AdherenceSummaryResponse summaryToday() {
        arrange();
        return service.summary(1L, Role.PARENT, null, null);
    }

    private AdherenceSummaryResponse summaryOn(LocalDate date) {
        arrange();
        return service.summary(1L, Role.PARENT, null, date);
    }

    /** Turns the lists above into mock answers ("what the database returns"). */
    private void arrange() {
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(anyLong()))
                .thenAnswer(inv -> medications.stream().filter(Medication::isActive).toList());
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(anyCollection()))
                .thenAnswer(inv -> List.copyOf(activeSchedules));
        when(inventoryRepository.findByMedicationIdIn(anyCollection()))
                .thenAnswer(inv -> List.copyOf(stock));
        when(logRepository.findByParentAndDateAndStatus(anyLong(), any(LocalDate.class), eq(LogStatus.TAKEN)))
                .thenAnswer(inv -> takenLogs.stream()
                        .filter(l -> l.getScheduledDate().equals(inv.getArgument(1, LocalDate.class)))
                        .toList());
    }

    private Medication medication(long id, String name, MedicationType type, String dosage, int stockQuantity) {
        Medication m = new Medication(parent, name, type, "500 mg", new BigDecimal(dosage), "Daily",
                FoodInstruction.ANY_TIME, null, LocalDate.of(2026, 10, 1), null);
        ReflectionTestUtils.setField(m, "id", id);
        medications.add(m);
        stock.add(new MedicationInventory(m, new BigDecimal(stockQuantity), new BigDecimal("3")));
        return m;
    }

    private MedicationSchedule schedule(Medication m, long id, int hour) {
        MedicationSchedule s = new MedicationSchedule(m, LocalTime.of(hour, 0));
        ReflectionTestUtils.setField(s, "id", id);
        activeSchedules.add(s);
        return s;
    }

    /** "n tablets of this medicine were confirmed on that date" (the count query for that day returns n). */
    private void tabletsTaken(Medication m, LocalDate date, long n) {
        when(tabletRepository.countByStatusBetween(eq(m.getId()), eq(TabletStatus.TAKEN),
                eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay()))).thenReturn(n);
    }

    private void takenLog(Medication m, MedicationSchedule s, LocalDate date) {
        takenLogs.add(new MedicationLog(m, s, date, BigDecimal.TEN, LocalDateTime.of(date, LocalTime.of(9, 0))));
    }
}
