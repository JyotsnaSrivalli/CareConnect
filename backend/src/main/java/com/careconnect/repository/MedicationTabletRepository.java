package com.careconnect.repository;

import com.careconnect.entity.MedicationTablet;
import com.careconnect.entity.TabletStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MedicationTabletRepository extends JpaRepository<MedicationTablet, Long> {

    List<MedicationTablet> findByMedicationIdInOrderBySlotNumber(Collection<Long> medicationIds);

    /** Ownership check that returns only the medication id (no entity cached, nothing can go stale). */
    @Query("select t.medication.id from MedicationTablet t "
            + "where t.id = :id and t.medication.parent.id = :parentId and t.medication.active = true")
    Optional<Long> findMedicationIdOwnedBy(@Param("id") Long id, @Param("parentId") Long parentId);

    /** SELECT ... FOR UPDATE: reads the latest committed row and blocks other writers until we commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MedicationTablet t where t.id = :id")
    Optional<MedicationTablet> findByIdForUpdate(@Param("id") Long id);

    /**
     * How many tablets of one medicine have this status (we pass TAKEN) with taken_at in [rangeStart, rangeEnd). Read-only, changes nothing.
     * Tablets marked taken before the Day 4 upgrade have taken_at = NULL, so they can never match a date range.
     */
    @Query("select count(t) from MedicationTablet t where t.medication.id = :medicationId "
            + "and t.status = :status and t.takenAt >= :rangeStart and t.takenAt < :rangeEnd")
    long countByStatusBetween(@Param("medicationId") Long medicationId,
                              @Param("status") TabletStatus status,
                              @Param("rangeStart") LocalDateTime rangeStart,
                              @Param("rangeEnd") LocalDateTime rangeEnd);

    long countByMedicationId(Long medicationId);

    long countByMedicationIdAndStatus(Long medicationId, TabletStatus status);

    List<MedicationTablet> findByMedicationIdAndStatusOrderBySlotNumberDesc(Long medicationId, TabletStatus status);

    @Query("select coalesce(max(t.slotNumber), 0) from MedicationTablet t where t.medication.id = :medicationId")
    Integer findMaxSlotNumber(@Param("medicationId") Long medicationId);
}
