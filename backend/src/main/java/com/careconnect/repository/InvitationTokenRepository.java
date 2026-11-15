package com.careconnect.repository;

import com.careconnect.entity.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {

    Optional<InvitationToken> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomic "use once" guard: only one caller can flip used=false to true.
     * Returns 1 if this call consumed the code, 0 if someone else already did.
     */
    @Modifying
    @Query("update InvitationToken t set t.used = true where t.id = :id and t.used = false")
    int markUsed(@Param("id") Long id);
}
