package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.menu.domain.ModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierOption;
import com.qr_ordering_system.app.menu.dto.mapper.ModifierGroupMapper;
import com.qr_ordering_system.app.menu.dto.request.ModifierGroupCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.ModifierGroupUpdateRequest;
import com.qr_ordering_system.app.menu.dto.request.ModifierOptionDTO;
import com.qr_ordering_system.app.menu.dto.response.ModifierGroupResponse;
import com.qr_ordering_system.app.menu.repository.MenuItemModifierGroupRepository;
import com.qr_ordering_system.app.menu.repository.ModifierGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ModifierGroupService {

    private final ModifierGroupRepository modifierGroupRepository;
    private final MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    public ModifierGroupResponse create (ModifierGroupCreateRequest request) {
        // Shared null-safe validation. The create path used to unbox minSelect /
        // maxSelect directly, so an omitted field threw a 500 NullPointerException.
        validateConstraints(request.isRequired(), request.minSelect(), request.maxSelect());

        ModifierGroup modifierGroup = new ModifierGroup();
        modifierGroup.setLocalizedNames(new HashMap<>(request.localizedNames()));
        modifierGroup.setIsRequired(request.isRequired());
        modifierGroup.setMinSelect(request.minSelect());
        modifierGroup.setMaxSelect(request.maxSelect());
        modifierGroup.setIsActive(request.isActive() != null ? request.isActive() : true);

        if (request.options() != null) {
            for (ModifierOptionDTO optDTO : request.options()){
                ModifierOption option = new ModifierOption();
                option.setLocalizedNames(new HashMap<>(optDTO.localizedNames()));
                option.setExtraPrice(optDTO.extraPrice());
                option.setIsAvailable(optDTO.isAvailable() != null ? optDTO.isAvailable() : true);
                option.setModifierGroup(modifierGroup); // Bi-directional mapping
                modifierGroup.getOptions().add(option);
            }
        }

        return ModifierGroupMapper.toresponse(modifierGroupRepository.save(modifierGroup));
    }

    public List<ModifierGroupResponse> getAll() {
         return modifierGroupRepository.findAllWithOptions().stream()
                .map(ModifierGroupMapper::toresponse)
                .toList();
    }

    public ModifierGroupResponse update(Long id, ModifierGroupUpdateRequest request) {
        validateConstraints(request.isRequired(), request.minSelect(), request.maxSelect());

        ModifierGroup group = modifierGroupRepository.findWithOptionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModifierGroup not found: " + id));

        group.setLocalizedNames(new HashMap<>(request.localizedNames()));
        group.setIsRequired(request.isRequired() != null ? request.isRequired() : false);
        group.setMinSelect(request.minSelect());
        group.setMaxSelect(request.maxSelect());
        if (request.isActive() != null) {
            group.setIsActive(request.isActive());
        }

        // Reconcile options BY ID.
        //
        // This used to clear the collection and re-insert every option, which
        // gave each option a brand-new id on every save. Phase 4 order lines
        // reference modifier_option_id, so re-saving a menu would orphan every
        // historical order line. Now: an option that carries a known id is
        // updated in place, unknown ids are rejected, and options the caller
        // dropped are removed.
        Map<Long, ModifierOption> existingById = group.getOptions().stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(ModifierOption::getId, Function.identity(), (a, b) -> a));

        List<ModifierOption> desired = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        for (ModifierOptionDTO dto : request.options() == null ? List.<ModifierOptionDTO>of() : request.options()) {
            ModifierOption option = dto.id() == null ? null : existingById.get(dto.id());

            if (dto.id() != null && option == null) {
                throw new ResourceNotFoundException(
                        "Modifier option " + dto.id() + " does not belong to modifier group " + id);
            }
            if (dto.id() != null && !seenIds.add(dto.id())) {
                throw new IllegalArgumentException("Duplicate modifier option id in request: " + dto.id());
            }

            if (option == null) {
                option = new ModifierOption();
                option.setModifierGroup(group);
            }
            option.setLocalizedNames(new HashMap<>(dto.localizedNames()));
            option.setExtraPrice(dto.extraPrice());
            option.setIsAvailable(dto.isAvailable() != null ? dto.isAvailable() : true);
            desired.add(option);
        }

        // orphanRemoval=true turns the difference into DELETEs on flush.
        group.getOptions().clear();
        group.getOptions().addAll(desired);

        return ModifierGroupMapper.toresponse(modifierGroupRepository.save(group));
    }

    public void delete(Long id) {
        ModifierGroup group = modifierGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModifierGroup not found: " + id));

        long linkedItems = menuItemModifierGroupRepository.findAllByModifierGroupId(id).size();
        if (linkedItems > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete modifier group " + id + " because it is still assigned to " + linkedItems + " menu item(s)");
        }

        // orphanRemoval=true on options handles child deletion automatically
        modifierGroupRepository.delete(group);
    }

    private void validateConstraints(Boolean isRequired, Integer minSelect, Integer maxSelect){
        if (minSelect != null && maxSelect != null && minSelect > maxSelect) {
            throw new IllegalArgumentException("minSelect (" + minSelect + ") cannot be greater than maxSelect (" + maxSelect + ")");
        }
        if (Boolean.TRUE.equals(isRequired) && (minSelect == null || minSelect < 1)) {
            throw new IllegalArgumentException("When isRequired is true, minSelect must be at least 1");
        }
    }
}
