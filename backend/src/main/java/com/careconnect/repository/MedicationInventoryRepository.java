package com.careconnect.repository;

import com.careconnect.entity.MedicationInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MedicationInventoryRepository extends JpaRepository<MedicationInventory, Long> {

    Optional<MedicationInventory> findByMedicationId(Long medicationId);

    List<MedicationInventory> findByMedicationIdIn(Collection<Long> medicationIds);

    /**
     * Locks this medication's stock row (SELECT ... FOR UPDATE) for the rest of the transaction.
     * Two requests changing the same stock now run one after the other, never at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MedicationInventory i where i.medication.id = :medicationId")
    Optional<MedicationInventory> findByMedicationIdForUpdate(@Param("medicationId") Long medicationId);
}
