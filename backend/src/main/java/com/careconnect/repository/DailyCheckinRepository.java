package com.careconnect.repository;

import com.careconnect.entity.DailyCheckin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {

    /** Every lookup includes the parent id, so one parent can never load another parent's check-in. */
    Optional<DailyCheckin> findByParentIdAndCheckinDate(Long parentId, LocalDate checkinDate);

    /** Newest first. The Pageable is only used to say "give me at most N rows". */
    List<DailyCheckin> findByParentIdOrderByCheckinDateDesc(Long parentId, Pageable pageable);
}
