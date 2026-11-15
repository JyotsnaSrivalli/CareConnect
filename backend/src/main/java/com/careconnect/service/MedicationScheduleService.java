package com.careconnect.service;

import com.careconnect.dto.ScheduleRequest;
import com.careconnect.dto.ScheduleResponse;
import com.careconnect.entity.Medication;
import com.careconnect.entity.MedicationSchedule;
import com.careconnect.entity.Role;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationLogRepository;
import com.careconnect.repository.MedicationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Schedules are never hard-deleted: removing one sets active=false, so dose history (medication_logs)
 * that points at it is preserved.
 */
@Service
public class MedicationScheduleService {

    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationLogRepository logRepository;
    private final MedicationService medicationService;

    public MedicationScheduleService(MedicationScheduleRepository scheduleRepository,
                                     MedicationLogRepository logRepository,
                                     MedicationService medicationService) {
        this.scheduleRepository = scheduleRepository;
        this.logRepository = logRepository;
        this.medicationService = medicationService;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(Long userId, Role role, Long medicationId) {
        medicationService.requireViewable(userId, role, medicationId);
        return scheduleRepository.findByMedicationIdAndActiveTrueOrderByDoseTime(medicationId).stream()
                .map(ScheduleResponse::from).toList();
    }

    @Transactional
    public ScheduleResponse add(Long parentId, Long medicationId, ScheduleRequest request) {
        Medication medication = medicationService.requireOwned(parentId, medicationId);
        LocalTime time = MedicationService.normalize(request.doseTime());

        // (medication, time) is unique in the table even for removed schedules,
        // so adding a time that was removed earlier simply switches that row back on.
        Optional<MedicationSchedule> sameTime = scheduleRepository.findByMedicationIdAndDoseTime(medication.getId(), time);
        if (sameTime.isPresent()) {
            if (sameTime.get().isActive()) {
                throw ApiException.conflict("This medication already has a dose at that time");
            }
            sameTime.get().activate();
            scheduleRepository.save(sameTime.get());
            return ScheduleResponse.from(sameTime.get());
        }
        MedicationSchedule schedule = new MedicationSchedule(medication, time);
        scheduleRepository.save(schedule);
        return ScheduleResponse.from(schedule);
    }

    /**
     * Changes a dose time. If the old schedule already has history, the history must keep its ORIGINAL time,
     * so the old schedule is retired and the new time becomes a separate schedule (the returned id can differ).
     */
    @Transactional
    public ScheduleResponse update(Long parentId, Long medicationId, Long scheduleId, ScheduleRequest request) {
        Medication medication = medicationService.requireOwned(parentId, medicationId);
        MedicationSchedule schedule = findActiveSchedule(medication, scheduleId);
        LocalTime time = MedicationService.normalize(request.doseTime());
        if (time.equals(schedule.getDoseTime())) {
            return ScheduleResponse.from(schedule);
        }

        Optional<MedicationSchedule> atNewTime = scheduleRepository.findByMedicationIdAndDoseTime(medication.getId(), time);
        if (atNewTime.isPresent() && atNewTime.get().isActive()) {
            throw ApiException.conflict("This medication already has a dose at that time");
        }

        boolean hasHistory = logRepository.existsByScheduleId(schedule.getId());
        if (!hasHistory && atNewTime.isEmpty()) {
            schedule.changeTime(time);                       // nothing depends on the old time: edit in place
            return ScheduleResponse.from(schedule);
        }

        schedule.deactivate();
        MedicationSchedule target = atNewTime.orElseGet(() -> new MedicationSchedule(medication, time));
        target.activate();
        scheduleRepository.save(target);
        return ScheduleResponse.from(target);
    }

    /** Soft delete. */
    @Transactional
    public void remove(Long parentId, Long medicationId, Long scheduleId) {
        Medication medication = medicationService.requireOwned(parentId, medicationId);
        findActiveSchedule(medication, scheduleId).deactivate();
    }

    private MedicationSchedule findActiveSchedule(Medication medication, Long scheduleId) {
        return scheduleRepository.findByIdAndMedicationIdAndActiveTrue(scheduleId, medication.getId())
                .orElseThrow(() -> ApiException.notFound("Schedule not found"));
    }
}
