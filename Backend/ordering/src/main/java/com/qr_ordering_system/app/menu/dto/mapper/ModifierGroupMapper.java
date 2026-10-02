package com.qr_ordering_system.app.menu.dto.mapper;

import com.qr_ordering_system.app.menu.domain.ModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierOption;
import com.qr_ordering_system.app.menu.dto.response.ModifierGroupResponse;
import com.qr_ordering_system.app.menu.dto.response.ModifierOptionResponse;

import java.util.Collections;
import java.util.List;

public class ModifierGroupMapper {

    private ModifierGroupMapper() {}

    public static ModifierGroupResponse toresponse (ModifierGroup group) {
        List<ModifierOptionResponse> optionResponses = group.getOptions() == null
                ? Collections.emptyList()
                : group.getOptions().stream()
                    .map(ModifierGroupMapper::toOptionResponse)
                    .toList();

        return new ModifierGroupResponse(
                group.getId(),
                group.getLocalizedNames(),
                group.getIsRequired(),
                group.getMinSelect(),
                group.getMaxSelect(),
                group.getIsActive(),
                optionResponses,
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    public static ModifierOptionResponse toOptionResponse (ModifierOption option) {
        return new ModifierOptionResponse(
                option.getId(),
                option.getLocalizedNames(),
                option.getExtraPrice(),
                option.getIsAvailable()
        );
    }
}
