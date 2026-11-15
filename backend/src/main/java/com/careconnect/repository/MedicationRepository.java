package com.careconnect.repository;

import com.careconnect.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findByParentIdAndActiveTrueOrderByCreatedAtDesc(Long parentId);

    /** Ownership is part of the query, so another parent's medication simply is not found. */
    Optional<Medication> findByIdAndParentIdAndActiveTrue(Long id, Long parentId);

    Optional<Medication> findByIdAndActiveTrue(Long id);
}
