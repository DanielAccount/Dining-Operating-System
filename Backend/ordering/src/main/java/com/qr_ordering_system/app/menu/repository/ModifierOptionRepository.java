package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.ModifierOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModifierOptionRepository extends JpaRepository<ModifierOption, Long> {
    List<ModifierOption> findAllByModifierGroupId(Long modifierGroupId);
}

// Note: you'll mostly manage options through ModifierGroup.getOptions() (cascade + orphanRemoval), but this repo is handy
// for standalone lookups.