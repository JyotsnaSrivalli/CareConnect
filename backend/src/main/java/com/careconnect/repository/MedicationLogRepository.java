package com.careconnect.repository;

import com.careconnect.entity.LogStatus;
import com.careconnect.entity.MedicationLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    /** SELECT ... FOR UPDATE: always reads the latest committed row and blocks other writers until we commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MedicationLog l where l.schedule.id = :scheduleId and l.scheduledDate = :date")
    Optional<MedicationLog> findForUpdate(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MedicationLog l where l.id = :id")
    Optional<MedicationLog> findByIdForUpdate(@Param("id") Long id);

    /** Ownership check that returns only the medication id (no entity is loaded, so nothing goes stale). */
    @Query("select l.medication.id from MedicationLog l where l.id = :id and l.medication.parent.id = :parentId")
    Optional<Long> findMedicationIdOwnedBy(@Param("id") Long id, @Param("parentId") Long parentId);

    /** All confirmed doses of one parent on one day, including ones whose schedule/medication is now inactive. */
    @Query("select l from MedicationLog l join fetch l.medication m join fetch l.schedule "
            + "where m.parent.id = :parentId and l.scheduledDate = :date and l.status = :status")
    List<MedicationLog> findByParentAndDateAndStatus(@Param("parentId") Long parentId,
                                                     @Param("date") LocalDate date,
                                                     @Param("status") LogStatus status);

    boolean existsByScheduleId(Long scheduleId);
}
