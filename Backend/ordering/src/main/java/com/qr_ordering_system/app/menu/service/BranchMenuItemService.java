package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.BranchAccessResolver;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.menu.domain.BranchMenuItem;
import com.qr_ordering_system.app.menu.domain.MenuAvailability;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.dto.mapper.CategoryMapper;
import com.qr_ordering_system.app.menu.dto.request.BranchMenuItemBulkUpsertRequest;
import com.qr_ordering_system.app.menu.dto.request.BranchMenuItemUpsertRequest;
import com.qr_ordering_system.app.menu.dto.response.BranchMenuItemResponse;
import com.qr_ordering_system.app.menu.repository.BranchMenuItemRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchMenuItemService {

    private final BranchMenuItemRepository branchMenuItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final BranchAccessResolver branchAccessResolver;

    /**
     * Resolves which branch to operate on:
     *  - branch-scoped users (manager/staff) always use their own branch
     *  - tenant-wide users (owner) must supply the branch explicitly
     */
    private Branch resolveBranch(Long branchId) {
        return branchAccessResolver.resolve(branchId);
    }

    @Transactional(readOnly = true)
    public List<BranchMenuItemResponse> listForBranch(Long branchId) {
        Branch branch = resolveBranch(branchId);

        // Every menu item in the tenant gets a row; overrides are merged in.
        List<MenuItem> items = menuItemRepository.findAllActiveWithCategory();
        Map<Long, BranchMenuItem> overrides = branchMenuItemRepository
                .findAllForBranch(branch.getId())
                .stream()
                .collect(Collectors.toMap(bmi -> bmi.getMenuItem().getId(), Function.identity()));

        return items.stream()
                .map(item -> toRow(item, overrides.get(item.getId())))
                .toList();
    }

    public BranchMenuItemResponse upsert(Long branchId, Long menuItemId, BranchMenuItemUpsertRequest request) {
        Branch branch = resolveBranch(branchId);
        MenuItem item = menuItemRepository.findActiveWithCategoryById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found or inactive: " + menuItemId));

        BranchMenuItem row = branchMenuItemRepository
                .findByBranchIdAndMenuItemId(branch.getId(), item.getId())
                .orElseGet(() -> {
                    BranchMenuItem created = new BranchMenuItem();
                    created.setBranch(branch);
                    created.setMenuItem(item);
                    return created;
                });

        row.setPriceOverride(request.priceOverride());
        row.setIsAvailable(request.isAvailable());
        if (request.isInStock() != null) {
            row.setIsInStock(request.isInStock());
        }

        BranchMenuItem saved = branchMenuItemRepository.save(row);
        return toRow(item, saved);
    }

    public List<BranchMenuItemResponse> bulkUpsert(Long branchId, BranchMenuItemBulkUpsertRequest request) {
        Branch branch = resolveBranch(branchId);
        List<Long> itemIds = request.items().stream()
                .map(BranchMenuItemBulkUpsertRequest.Item::menuItemId)
                .toList();

        if (itemIds.size() != new HashSet<>(itemIds).size()) {
            throw new IllegalArgumentException("Duplicate menu item ids are not allowed");
        }

        Map<Long, MenuItem> itemsById = menuItemRepository
                .findAllActiveWithCategoryByIdIn(itemIds)
                .stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        if (itemsById.size() != itemIds.size()) {
            List<Long> missingIds = itemIds.stream()
                    .distinct()
                    .filter(id -> !itemsById.containsKey(id))
                    .toList();
            throw new ResourceNotFoundException("Menu item(s) not found or inactive: " + missingIds);
        }

        Map<Long, BranchMenuItem> existing = branchMenuItemRepository
                .findAllForBranchAndMenuItemIds(branch.getId(), itemsById.keySet())
                .stream()
                .collect(Collectors.toMap(bmi -> bmi.getMenuItem().getId(), Function.identity()));

        List<BranchMenuItem> rows = request.items().stream()
                .map(dto -> {
                    MenuItem item = itemsById.get(dto.menuItemId());
                    BranchMenuItem row = existing.get(item.getId());
                    if (row == null) {
                        row = new BranchMenuItem();
                        row.setBranch(branch);
                        row.setMenuItem(item);
                    }
                    row.setPriceOverride(dto.priceOverride());
                    row.setIsAvailable(dto.isAvailable());
                    if (dto.isInStock() != null) {
                        row.setIsInStock(dto.isInStock());
                    }
                    return row;
                })
                .toList();

        Map<Long, BranchMenuItem> savedByItemId = branchMenuItemRepository.saveAll(rows)
                .stream()
                .collect(Collectors.toMap(row -> row.getMenuItem().getId(), Function.identity()));

        return request.items().stream()
                .map(dto -> toRow(itemsById.get(dto.menuItemId()), savedByItemId.get(dto.menuItemId())))
                .toList();
    }

    /**
     * Reset: removes the branch row entirely.
     * Absence of a row means "follow global defaults" (base price, available).
     *
     * Physically deleted rather than soft deleted - see
     * {@link BranchMenuItemRepository#hardDelete}. The loaded row supplies the
     * tenant id, so the native DELETE cannot be pointed at another tenant's row.
     *
     * Idempotent: resetting a row that is already at global defaults succeeds
     * with 204 instead of 404. A double-click, a retry after a timeout, or two
     * tabs racing all land on the same end state, and none of them is an error -
     * the caller asked for "no override", and there is none.
     */
    public void reset(Long branchId, Long menuItemId) {
        Branch branch = resolveBranch(branchId);

        branchMenuItemRepository.findByBranchIdAndMenuItemId(branch.getId(), menuItemId)
                .ifPresent(row -> branchMenuItemRepository.hardDelete(row.getId(), row.getTenantId()));
    }

    private BranchMenuItemResponse toRow(MenuItem item, BranchMenuItem override) {
        var basePrice = item.getBasePrice();
        var priceOverride = override != null ? override.getPriceOverride() : null;

        // A missing row means "follow global defaults" at every level.
        boolean isAvailable = override != null ? override.getIsAvailable() : true;
        boolean isInStock = override != null ? override.getIsInStock() : true;
        boolean isActive = item.getIsActive();

        return new BranchMenuItemResponse(
                override != null ? override.getId() : null,
                item.getId(),
                item.getLocalizedNames(),
                CategoryMapper.toSummary(item.getCategory()),
                basePrice,
                priceOverride,
                priceOverride != null ? priceOverride : basePrice,
                isActive,
                isAvailable,
                isInStock,
                MenuAvailability.isOrderable(isActive, isAvailable, isInStock)
        );
    }
}
