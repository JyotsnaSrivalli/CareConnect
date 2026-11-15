package com.careconnect.service;

import com.careconnect.dto.MedicationRequest;
import com.careconnect.dto.MedicationResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import com.careconnect.repository.MedicationRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import com.careconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

    @Mock private MedicationRepository medicationRepository;
    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private MedicationInventoryRepository inventoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private FamilyService familyService;
    @Mock private TabletService tabletService;

    private MedicationService service;
    private User parent;   // id 1
    private User child;    // id 2

    @BeforeEach
    void setUp() {
        service = new MedicationService(medicationRepository, scheduleRepository, inventoryRepository,
                userRepository, familyService, tabletService);
        parent = user(1L, Role.PARENT);
        child = user(2L, Role.CHILD);
    }

    // ---------- create ----------
    @Test
    void parentCanAddMedicationWithStockAndSortedUniqueDoseTimes() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        MedicationRequest request = request(LocalDate.now(), null,
                List.of(LocalTime.of(20, 0), LocalTime.of(8, 0), LocalTime.of(8, 0, 30)));   // 08:00:30 == 08:00

        MedicationResponse response = service.create(1L, request);

        ArgumentCaptor<Medication> saved = ArgumentCaptor.forClass(Medication.class);
        verify(medicationRepository).save(saved.capture());
        assertEquals(1L, saved.getValue().getParent().getId());          // owner comes from the caller, not the request
        assertEquals("Paracetamol", response.name());
        assertEquals(QuantityUnit.TABLET, response.quantityUnit());
        assertEquals(FoodInstruction.AFTER_FOOD, response.foodInstruction());
        assertTrue(response.active());
        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                response.schedules().stream().map(s -> s.doseTime()).toList());
        assertEquals(0, new BigDecimal("20").compareTo(response.inventory().currentQuantity()));
        assertFalse(response.inventory().lowStock());
        verify(inventoryRepository).save(any(MedicationInventory.class));
        verify(tabletService).createStrip(any(Medication.class), eq(new BigDecimal("20")));   // 20 compartments
    }

    @Test
    void syrupGetsNoBlisterStrip() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));

        service.create(1L, new MedicationRequest("Cough syrup", MedicationType.SYRUP, null, new BigDecimal("5"),
                "Twice daily", FoodInstruction.ANY_TIME, null, LocalDate.now(), null,
                new BigDecimal("100.5"), new BigDecimal("10"), List.of()));

        verify(tabletService, never()).createStrip(any(), any());
    }

    @Test
    void tabletCountMustBeAWholeNumber() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));

        ApiException ex = assertThrows(ApiException.class, () -> service.create(1L, new MedicationRequest(
                "Paracetamol", MedicationType.TABLET, null, new BigDecimal("1"), "Daily", FoodInstruction.ANY_TIME,
                null, LocalDate.now(), null, new BigDecimal("2.5"), new BigDecimal("1"), List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void medicineCannotBeSwitchedBetweenTabletAndSyrup() {
        Medication med = medication(10L, parent);                     // a TABLET
        when(medicationRepository.findByIdAndParentIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(med));

        ApiException ex = assertThrows(ApiException.class, () -> service.update(1L, 10L, new MedicationRequest(
                "Paracetamol", MedicationType.SYRUP, null, new BigDecimal("5"), "Daily", FoodInstruction.ANY_TIME,
                null, LocalDate.now(), null, null, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(MedicationType.TABLET, med.getType());
    }

    @Test
    void tabletCanBecomeCapsule() {
        Medication med = medication(10L, parent);
        when(medicationRepository.findByIdAndParentIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(med));

        MedicationResponse response = service.update(1L, 10L, new MedicationRequest("Paracetamol",
                MedicationType.CAPSULE, null, new BigDecimal("1"), "Daily", FoodInstruction.ANY_TIME, null,
                LocalDate.now(), null, null, null, null));

        assertEquals(QuantityUnit.CAPSULE, response.quantityUnit());
    }

    @Test
    void childCannotAddMedication() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(2L, request(LocalDate.now(), null, List.of())));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        LocalDate start = LocalDate.of(2026, 5, 10);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(1L, request(start, start.minusDays(1), List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ---------- read as parent ----------
    @Test
    void parentCanListOwnMedicationsWithSchedulesAndInventory() {
        Medication med = medication(10L, parent);
        when(medicationRepository.findByParentIdAndActiveTrueOrderByCreatedAtDesc(1L)).thenReturn(List.of(med));
        when(scheduleRepository.findByMedicationIdInAndActiveTrueOrderByDoseTime(any()))
                .thenReturn(List.of(new MedicationSchedule(med, LocalTime.of(8, 0)),
                        new MedicationSchedule(med, LocalTime.of(20, 0))));
        when(inventoryRepository.findByMedicationIdIn(any()))
                .thenReturn(List.of(new MedicationInventory(med, new BigDecimal("20"), new BigDecimal("5"))));

        List<MedicationResponse> result = service.list(1L, Role.PARENT, null);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).schedules().size());
        assertEquals(0, new BigDecimal("20").compareTo(result.get(0).inventory().currentQuantity()));
    }

    @Test
    void parentCannotGetAnotherParentsMedication() {
        when(medicationRepository.findByIdAndParentIdAndActiveTrue(10L, 99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.get(99L, Role.PARENT, 10L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---------- read as child ----------
    @Test
    void childWithApprovedConnectionCanViewMedication() {
        Medication med = medication(10L, parent);
        when(medicationRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(med));

        MedicationResponse response = service.get(2L, Role.CHILD, 10L);

        assertEquals("Paracetamol", response.name());
        verify(familyService).assertChildCanAccessParent(2L, 1L);   // checked against the medication's OWNER
    }

    @Test
    void childWithoutApprovedConnectionCannotViewMedication() {
        Medication med = medication(10L, parent);
        when(medicationRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(med));
        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanAccessParent(2L, 1L);

        ApiException ex = assertThrows(ApiException.class, () -> service.get(2L, Role.CHILD, 10L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void childListNeedsParentIdAndApprovedConnection() {
        ApiException missing = assertThrows(ApiException.class, () -> service.list(2L, Role.CHILD, null));
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatus());

        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanAccessParent(2L, 1L);
        ApiException denied = assertThrows(ApiException.class, () -> service.list(2L, Role.CHILD, 1L));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatus());
        verify(medicationRepository, never()).findByParentIdAndActiveTrueOrderByCreatedAtDesc(any());
    }

    // ---------- update / delete ----------
    @Test
    void childCannotUpdateOrDeleteMedication() {
        // a child owns nothing, so the ownership query finds nothing
        when(medicationRepository.findByIdAndParentIdAndActiveTrue(10L, 2L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.update(2L, 10L, request(LocalDate.now(), null, null)));
        assertThrows(ApiException.class, () -> service.delete(2L, 10L));
    }

    @Test
    void parentCanUpdateOwnMedication() {
        Medication med = medication(10L, parent);
        when(medicationRepository.findByIdAndParentIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(med));

        MedicationResponse response = service.update(1L, 10L, new MedicationRequest("Dolo 650", MedicationType.TABLET,
                "650 mg", new BigDecimal("1"), "Once daily", FoodInstruction.WITH_FOOD, "  ", LocalDate.now(),
                null, null, null, null));

        assertEquals("Dolo 650", response.name());
        assertEquals("Once daily", response.frequencyLabel());
        assertNull(response.notes());                       // blank notes are stored as null
    }

    @Test
    void deleteIsASoftDelete() {
        Medication med = medication(10L, parent);
        when(medicationRepository.findByIdAndParentIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(med));

        service.delete(1L, 10L);

        assertFalse(med.isActive());
        verify(medicationRepository, never()).delete(any());
    }

    // ---------- helpers ----------
    private MedicationRequest request(LocalDate start, LocalDate end, List<LocalTime> times) {
        return new MedicationRequest("Paracetamol", MedicationType.TABLET, "500 mg", new BigDecimal("1"),
                "Twice daily", FoodInstruction.AFTER_FOOD, "With water", start, end,
                new BigDecimal("20"), new BigDecimal("5"), times);
    }

    private User user(long id, Role role) {
        User u = new User("User" + id, "u" + id + "@test.com", "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Medication medication(long id, User owner) {
        Medication m = new Medication(owner, "Paracetamol", MedicationType.TABLET, "500 mg", new BigDecimal("1"),
                "Twice daily", FoodInstruction.AFTER_FOOD, "With water", LocalDate.now(), null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
