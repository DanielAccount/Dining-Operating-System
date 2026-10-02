package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.tenant.BranchAccessResolver;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.menu.domain.BranchMenuItem;
import com.qr_ordering_system.app.menu.domain.MenuAvailability;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierOption;
import com.qr_ordering_system.app.menu.dto.response.CustomerMenuCategoryResponse;
import com.qr_ordering_system.app.menu.dto.response.CustomerMenuItemResponse;
import com.qr_ordering_system.app.menu.dto.response.CustomerMenuModifierGroupResponse;
import com.qr_ordering_system.app.menu.dto.response.CustomerMenuModifierOptionResponse;
import com.qr_ordering_system.app.menu.repository.BranchMenuItemRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import com.qr_ordering_system.app.menu.repository.ModifierOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the customer-facing menu for one branch.
 *
 * This is the only place the availability rules are applied for customers, and
 * it is where Phase 3's QR menu should read from. It exists now, before the QR
 * layer, so the rule is settled and tested rather than re-invented inside a
 * controller later.
 *
 * Visibility, in one place:
 *   <ul>
 *     <li>retired item (is_active = false)        -> hidden</li>
 *     <li>branch does not offer it (is_available) -> hidden</li>
 *     <li>branch has run out (is_in_stock = false)-> shown greyed, soldOut</li>
 *   </ul>
 *
 * A sold-out item stays visible on purpose: telling a customer "we're out" is
 * honest and they will pick something else, whereas silently dropping the dish
 * reads as "this place stopped serving it".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerMenuService {

    private final MenuItemRepository menuItemRepository;
    private final BranchMenuItemRepository branchMenuItemRepository;
    private final ModifierOptionRepository modifierOptionRepository;
    private final BranchAccessResolver branchAccessResolver;

    public List<CustomerMenuCategoryResponse> menuForBranch(Long branchId) {
        Branch branch = branchAccessResolver.resolve(branchId);

        List<MenuItem> items = menuItemRepository.findAllActiveForCustomerMenu();
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, BranchMenuItem> overrides = branchMenuItemRepository
                .findAllForBranch(branch.getId())
                .stream()
                .collect(Collectors.toMap(bmi -> bmi.getMenuItem().getId(), Function.identity(), (a, b) -> a));

        // One extra query for every option on the whole menu, rather than one
        // per modifier group.
        Map<Long, List<ModifierOption>> optionsByGroup = loadOptions(items);

        // Insertion-ordered so categories come back in display order without a
        // second sort, and empty categories are dropped rather than rendered.
        Map<Long, CustomerMenuCategoryResponse> categories = new LinkedHashMap<>();
        Map<Long, List<CustomerMenuItemResponse>> itemsByCategory = new LinkedHashMap<>();

        for (MenuItem item : items) {
            BranchMenuItem override = overrides.get(item.getId());
            boolean isAvailable = override != null ? override.getIsAvailable() : true;
            boolean isInStock = override != null ? override.getIsInStock() : true;

            if (!MenuAvailability.isVisibleToCustomer(item.getIsActive(), isAvailable)) {
                continue;
            }

            var category = item.getCategory();
            categories.putIfAbsent(category.getId(),
                    new CustomerMenuCategoryResponse(category.getId(), category.getLocalizedNames(), List.of()));

            itemsByCategory
                    .computeIfAbsent(category.getId(), id -> new ArrayList<>())
                    .add(toCustomerItem(item, override, isInStock, optionsByGroup));
        }

        // Rebuild each category now that its item list is complete. Categories
        // with nothing visible were never added, so they do not appear.
        return categories.values().stream()
                .map(c -> new CustomerMenuCategoryResponse(
                        c.id(), c.localizedNames(), itemsByCategory.getOrDefault(c.id(), List.of())))
                .toList();
    }

    private CustomerMenuItemResponse toCustomerItem(
            MenuItem item,
            BranchMenuItem override,
            boolean isInStock,
            Map<Long, List<ModifierOption>> optionsByGroup) {

        var price = override != null && override.getPriceOverride() != null
                ? override.getPriceOverride()
                : item.getBasePrice();

        return new CustomerMenuItemResponse(
                item.getId(),
                item.getLocalizedNames(),
                item.getLocalizedDescriptions(),
                item.getImageUrl(),
                price,
                MenuAvailability.isSoldOut(item.getIsActive(), true, isInStock),
                toModifierGroups(item.getModifierGroups(), optionsByGroup)
        );
    }

    private List<CustomerMenuModifierGroupResponse> toModifierGroups(
            List<MenuItemModifierGroup> links,
            Map<Long, List<ModifierOption>> optionsByGroup) {

        List<CustomerMenuModifierGroupResponse> result = new ArrayList<>();

        for (MenuItemModifierGroup link : links) {
            if (!Boolean.TRUE.equals(link.getIsActive())) {
                continue;
            }

            ModifierGroup group = link.getModifierGroup();
            if (!Boolean.TRUE.equals(group.getIsActive())) {
                continue;
            }

            List<CustomerMenuModifierOptionResponse> options = optionsByGroup
                    .getOrDefault(group.getId(), List.of())
                    .stream()
                    .filter(o -> Boolean.TRUE.equals(o.getIsAvailable()))
                    .map(o -> new CustomerMenuModifierOptionResponse(
                            o.getId(), o.getLocalizedNames(), o.getExtraPrice()))
                    .toList();

            // Nothing selectable: showing an empty required group would leave
            // the customer unable to complete the order, so drop it.
            if (options.isEmpty()) {
                continue;
            }

            // The per-item link may override the group's own required flag.
            boolean required = link.getRequiredOverride() != null
                    ? link.getRequiredOverride()
                    : Boolean.TRUE.equals(group.getIsRequired());

            result.add(new CustomerMenuModifierGroupResponse(
                    group.getId(),
                    group.getLocalizedNames(),
                    required,
                    group.getMinSelect(),
                    group.getMaxSelect(),
                    options
            ));
        }

        return result;
    }

    private Map<Long, List<ModifierOption>> loadOptions(List<MenuItem> items) {
        Set<Long> groupIds = items.stream()
                .flatMap(item -> item.getModifierGroups().stream())
                .map(link -> link.getModifierGroup().getId())
                .collect(Collectors.toSet());

        if (groupIds.isEmpty()) {
            return Map.of();
        }

        return modifierOptionRepository.findAllByGroupIds(groupIds)
                .stream()
                .collect(Collectors.groupingBy(o -> o.getModifierGroup().getId()));
    }
}
