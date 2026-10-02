package com.qr_ordering_system.app.menu.dto.mapper;

import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import com.qr_ordering_system.app.menu.dto.response.MenuItemModifierGroupResponse;
import com.qr_ordering_system.app.menu.dto.response.MenuItemResponse;

import java.util.Collections;
import java.util.List;

public final class MenuItemMapper {

    private MenuItemMapper() {
    }

    public static MenuItemResponse toResponse(MenuItem item) {
        List<MenuItemModifierGroupResponse> mimgResponses = item.getModifierGroups() == null
                ? Collections.emptyList()
                : item.getModifierGroups().stream()
                    .map(MenuItemMapper::toMimgResponse)
                    .toList();

        return new MenuItemResponse(
                item.getId(),
                CategoryMapper.toSummary(item.getCategory()),
                item.getLocalizedNames(),
                item.getLocalizedDescriptions(),
                item.getBasePrice(),
                item.getImageUrl(),
                item.getDisplayOrder(),
                item.getIsActive(),
                mimgResponses,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    public static MenuItemModifierGroupResponse toMimgResponse(MenuItemModifierGroup mimg) {
        return new MenuItemModifierGroupResponse(
                mimg.getId(),
                mimg.getModifierGroup().getId(),
                mimg.getModifierGroup().getLocalizedNames(),
                mimg.getDisplayOrder(),
                mimg.getRequiredOverride(),
                mimg.getIsActive()
        );
    }
}
