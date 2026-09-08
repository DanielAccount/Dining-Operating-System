package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModifierGroupRepository extends JpaRepository <ModifierGroup,Long>{
    // Loads group with its options in one query (they use CascadeType.ALL on the entity)
    @Query("""
               select distinct mg from ModifierGroup mg
               left join fetch mg.options
               where mg.id = :id
               """)
    Optional<ModifierGroup> findWithOptionsById(@Param("id") Long id);
}
