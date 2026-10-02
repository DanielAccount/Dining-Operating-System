package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.ModifierOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ModifierOptionRepository extends JpaRepository<ModifierOption, Long> {

    /**
     * Selectable options for a set of modifier groups, in a stable order.
     *
     * Loaded in one query for the whole menu rather than per group: fetching
     * mg.options lazily would be an N+1 on every customer menu request, and
     * open-in-view is disabled so it would in fact fail rather than just be slow.
     */
    @Query("""
               select o from ModifierOption o
               where o.modifierGroup.id in :groupIds
               order by o.modifierGroup.id asc, o.id asc
               """)
    List<ModifierOption> findAllByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}
