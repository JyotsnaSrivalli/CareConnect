package com.careconnect.repository;

import com.careconnect.entity.ConnectionStatus;
import com.careconnect.entity.FamilyConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FamilyConnectionRepository extends JpaRepository<FamilyConnection, Long> {

    Optional<FamilyConnection> findByParentIdAndChildId(Long parentId, Long childId);

    /** Ownership check built into the query: a parent can only load THEIR OWN connections. */
    Optional<FamilyConnection> findByIdAndParentId(Long id, Long parentId);

    boolean existsByParentIdAndChildIdAndStatus(Long parentId, Long childId, ConnectionStatus status);

    /** Day 5: same check, but ALSO requires the parent to have ticked "share my daily check-ins". */
    boolean existsByParentIdAndChildIdAndStatusAndShareCheckinsTrue(Long parentId, Long childId, ConnectionStatus status);

    @Query("select c from FamilyConnection c join fetch c.parent join fetch c.child "
            + "where c.parent.id = :userId order by c.createdAt desc")
    List<FamilyConnection> findAllForParent(@Param("userId") Long userId);

    @Query("select c from FamilyConnection c join fetch c.parent join fetch c.child "
            + "where c.child.id = :userId order by c.createdAt desc")
    List<FamilyConnection> findAllForChild(@Param("userId") Long userId);
}
