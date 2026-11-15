package com.careconnect.service;

import com.careconnect.dto.ScheduleRequest;
import com.careconnect.dto.ScheduleResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationLogRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationScheduleServiceTest {

    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private MedicationLogRepository logRepository;
    @Mock private MedicationService medicationService;

    private MedicationScheduleService service;
    private Medication med;

    @BeforeEach
    void setUp() {
        service = new MedicationScheduleService(scheduleRepository, logRepository, medicationService);
        User parent = new User("Asha", "asha@test.com", "hash", Role.PARENT);
        ReflectionTestUtils.setField(parent, "id", 1L);
        med = new Medication(parent, "Paracetamol", MedicationType.TABLET, null, BigDecimal.ONE, "Twice daily",
                FoodInstruction.ANY_TIME, null, LocalDate.now(), null);
        ReflectionTestUtils.setField(med, "id", 10L);
    }

    @Test
    void parentCanAddScheduleAndSecondsAreDropped() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByMedicationIdAndDoseTime(10L, LocalTime.of(8, 0))).thenReturn(Optional.empty());

        ScheduleResponse response = service.add(1L, 10L, new ScheduleRequest(LocalTime.of(8, 0, 45)));

        ArgumentCaptor<MedicationSchedule> saved = ArgumentCaptor.forClass(MedicationSchedule.class);
        verify(scheduleRepository).save(saved.capture());
        assertEquals(LocalTime.of(8, 0), saved.getValue().getDoseTime());
        assertTrue(saved.getValue().isActive());
        assertEquals(LocalTime.of(8, 0), response.doseTime());
    }

    @Test
    void sameTimeTwiceIsConflict() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByMedicationIdAndDoseTime(10L, LocalTime.of(8, 0)))
                .thenReturn(Optional.of(schedule(5L, 8, 0, true)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.add(1L, 10L, new ScheduleRequest(LocalTime.of(8, 0))));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void addingATimeThatWasRemovedEarlierReactivatesThatSchedule() {
        MedicationSchedule removed = schedule(5L, 8, 0, false);
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByMedicationIdAndDoseTime(10L, LocalTime.of(8, 0))).thenReturn(Optional.of(removed));

        ScheduleResponse response = service.add(1L, 10L, new ScheduleRequest(LocalTime.of(8, 0)));

        assertTrue(removed.isActive());
        assertEquals(5L, response.id());              // same row, so old logs still belong to it
    }

    @Test
    void addingToSomeoneElsesMedicationFails() {
        when(medicationService.requireOwned(99L, 10L)).thenThrow(ApiException.notFound("Medication not found"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.add(99L, 10L, new ScheduleRequest(LocalTime.of(8, 0))));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void approvedChildCanListSchedulesInTimeOrder() {
        when(medicationService.requireViewable(2L, Role.CHILD, 10L)).thenReturn(med);
        when(scheduleRepository.findByMedicationIdAndActiveTrueOrderByDoseTime(10L)).thenReturn(List.of(
                schedule(1L, 8, 0, true), schedule(2L, 20, 0, true)));

        List<ScheduleResponse> result = service.list(2L, Role.CHILD, 10L);

        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)), result.stream().map(ScheduleResponse::doseTime).toList());
    }

    @Test
    void childWithoutAccessCannotListSchedules() {
        when(medicationService.requireViewable(2L, Role.CHILD, 10L)).thenThrow(ApiException.forbidden("no access"));

        ApiException ex = assertThrows(ApiException.class, () -> service.list(2L, Role.CHILD, 10L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(scheduleRepository);
    }

    // ---------- update ----------
    @Test
    void updateToAnActiveExistingTimeIsConflict() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByIdAndMedicationIdAndActiveTrue(5L, 10L)).thenReturn(Optional.of(schedule(5L, 8, 0, true)));
        when(scheduleRepository.findByMedicationIdAndDoseTime(10L, LocalTime.of(20, 0)))
                .thenReturn(Optional.of(schedule(6L, 20, 0, true)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.update(1L, 10L, 5L, new ScheduleRequest(LocalTime.of(20, 0))));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void updateWithoutHistoryChangesTheTimeInPlace() {
        MedicationSchedule schedule = schedule(5L, 8, 0, true);
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByIdAndMedicationIdAndActiveTrue(5L, 10L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.findByMedicationIdAndDoseTime(10L, LocalTime.of(9, 30))).thenReturn(Optional.empty());
        when(logRepository.existsByScheduleId(5L)).thenReturn(false);

        ScheduleResponse response = service.update(1L, 10L, 5L, new ScheduleRequest(LocalTime.of(9, 30)));

        assertEquals(LocalTime.of(9, 30), response.doseTime());
        assertEquals(5L, response.id());
        assertTrue(schedule.isActive());
    }

    @Test
    void updateWithHistoryRetiresOldScheduleAndKeepsItsLogsOnTheOriginalTime() {
        MedicationSchedule old = schedule(5L, 8, 0, true);
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByIdAndMedicationIdAndActiveTrue(5L, 10L)).thenReturn(Optional.of(old));
        when(scheduleRepository.findByMedicationIdAndDoseTime(10L, LocalTime.of(9, 30))).thenReturn(Optional.empty());
        when(logRepository.existsByScheduleId(5L)).thenReturn(true);

        ScheduleResponse response = service.update(1L, 10L, 5L, new ScheduleRequest(LocalTime.of(9, 30)));

        assertFalse(old.isActive());
        assertEquals(LocalTime.of(8, 0), old.getDoseTime());          // history still says 8:00
        assertEquals(LocalTime.of(9, 30), response.doseTime());       // new schedule at the new time
        verify(scheduleRepository).save(any(MedicationSchedule.class));
    }

    // ---------- remove (soft delete) ----------
    @Test
    void removingUnknownScheduleIsNotFound() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByIdAndMedicationIdAndActiveTrue(5L, 10L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.remove(1L, 10L, 5L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void removeDeactivatesTheScheduleInsteadOfDeletingIt() {
        MedicationSchedule schedule = schedule(5L, 8, 0, true);
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(scheduleRepository.findByIdAndMedicationIdAndActiveTrue(5L, 10L)).thenReturn(Optional.of(schedule));

        service.remove(1L, 10L, 5L);

        assertFalse(schedule.isActive());
        verify(scheduleRepository, never()).delete(any());             // so logs can never be cascade-deleted
        verify(scheduleRepository, never()).deleteById(any());
    }

    private MedicationSchedule schedule(long id, int hour, int minute, boolean active) {
        MedicationSchedule s = new MedicationSchedule(med, LocalTime.of(hour, minute));
        ReflectionTestUtils.setField(s, "id", id);
        if (!active) s.deactivate();
        return s;
    }
}
