package com.careconnect.repository;

import com.careconnect.entity.MedicationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    // ---- only ACTIVE schedules (removed ones are hidden everywhere in the app) ----
    List<MedicationSchedule> findByMedicationIdAndActiveTrueOrderByDoseTime(Long medicationId);

    List<MedicationSchedule> findByMedicationIdInAndActiveTrueOrderByDoseTime(Collection<Long> medicationIds);

    Optional<MedicationSchedule> findByIdAndMedicationIdAndActiveTrue(Long id, Long medicationId);

    /** Active schedule of an active medication owned by this parent (used when confirming a dose). */
    @Query("select s from MedicationSchedule s join fetch s.medication m "
            + "where s.id = :id and m.parent.id = :parentId and s.active = true and m.active = true")
    Optional<MedicationSchedule> findActiveOwned(@Param("id") Long id, @Param("parentId") Long parentId);

    // ---- any state: needed because (medication, time) is unique even for removed schedules ----
    Optional<MedicationSchedule> findByMedicationIdAndDoseTime(Long medicationId, LocalTime doseTime);
}
