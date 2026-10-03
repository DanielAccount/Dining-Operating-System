package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierGroup;
import com.qr_ordering_system.app.menu.dto.request.MenuItemModifierGroupDTO;
import com.qr_ordering_system.app.menu.repository.MenuItemModifierGroupRepository;
import com.qr_ordering_system.app.menu.repository.ModifierGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps a menu item's modifier-group links ({@code menu_item_modifier_groups})
 * in step with what the client sent.
 *
 * <p>A link is the row that says "item 7 offers the Extra Cheese group". It
 * carries three settings of its own - where it sits in the item's list, whether
 * it is mandatory for this item, and whether it is active - which is why it is
 * a table rather than a flag on the item.
 *
 * <p>The same job is needed twice: when an item is first created (all links are
 * new) and when it is updated (some exist, some are new, some were removed).
 * Both cases are the same operation - "make the stored links match this list" -
 * so there is one {@link #sync} method and {@link MenuItemService} calls it from
 * both places.
 *
 * <h2>The contract</h2>
 * <ul>
 *   <li>{@code null} list - the field was not sent. Leave the links untouched.
 *       This is what lets a small edit, or the retire toggle, go through the
 *       same endpoint without silently wiping the item's modifier groups.</li>
 *   <li>Non-null list - the list <em>is</em> the truth: links missing from it
 *       are removed, links already present are updated, the rest are created.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MenuItemModifierLinkService {

    private final MenuItemModifierGroupRepository linkRepository;
    private final ModifierGroupRepository modifierGroupRepository;

    /**
     * Makes the item's stored links match {@code requestedGroups}.
     *
     * <p>Order of work: validate everything first (so a bad payload changes
     * nothing), then remove what is no longer wanted, then add or update the
     * rest.
     */
    public void sync(MenuItem item, List<MenuItemModifierGroupDTO> requestedGroups) {
        if (requestedGroups == null) {
            return; // field omitted - keep what is there
        }

        validateAllGroupsExist(requestedGroups);

        Map<Long, MenuItemModifierGroup> existingLinks = indexByGroupId(item.getModifierGroups());

        removeUnwantedLinks(item, requestedGroups);
        createOrUpdateLinks(item, requestedGroups, existingLinks);
    }

    // ── Step 1: reject the whole payload if any group id is bogus ──────────

    /**
     * Fails fast with a 404 rather than part-way through.
     *
     * <p>One query for all the ids at once, so a payload of ten groups still
     * costs one round trip. The ids come back in a {@code Set}, so the error
     * message lists each unknown id once even if the payload repeated it.
     */
    private void validateAllGroupsExist(List<MenuItemModifierGroupDTO> requestedGroups) {
        if (requestedGroups.isEmpty()) {
            return;
        }

        Set<Long> requestedIds = requestedGroups.stream()
                .map(MenuItemModifierGroupDTO::modifierGroupId)
                .collect(Collectors.toSet());

        Set<Long> foundIds = modifierGroupRepository.findAllById(requestedIds).stream()
                .map(ModifierGroup::getId)
                .collect(Collectors.toSet());

        for (Long id : requestedIds) {
            if (!foundIds.contains(id)) {
                throw new ResourceNotFoundException("ModifierGroup not found: " + id);
            }
        }
    }

    // ── Step 2: drop the links that were not asked for ────────────────────

    /**
     * Removes links whose modifier group is absent from the request.
     *
     * <p>{@code orphanRemoval} on {@code MenuItem.modifierGroups} turns this
     * collection edit into an actual DELETE when the entity manager flushes, so
     * there is no need to call the repository per row.
     */
    private void removeUnwantedLinks(MenuItem item, List<MenuItemModifierGroupDTO> requestedGroups) {
        Set<Long> requestedIds = requestedGroups.stream()
                .map(MenuItemModifierGroupDTO::modifierGroupId)
                .collect(Collectors.toSet());

        item.getModifierGroups()
                .removeIf(link -> !requestedIds.contains(link.getModifierGroup().getId()));
    }

    // ── Step 3: update the links that survive, create the new ones ────────

    private void createOrUpdateLinks(
            MenuItem item,
            List<MenuItemModifierGroupDTO> requestedGroups,
            Map<Long, MenuItemModifierGroup> existingLinks) {

        Set<Long> alreadyHandled = new HashSet<>();
        int positionInPayload = 0;

        for (MenuItemModifierGroupDTO requested : requestedGroups) {

            // The same group listed twice in one payload: first mention wins.
            if (!alreadyHandled.add(requested.modifierGroupId())) {
                continue;
            }
            positionInPayload++;

            MenuItemModifierGroup existing = existingLinks.get(requested.modifierGroupId());

            if (existing != null) {
                applyRequestedChanges(existing, requested);
                linkRepository.save(existing);
            } else {
                createLink(item, requested, positionInPayload);
            }
        }
    }

    /**
     * Copies across only the settings the caller actually supplied.
     *
     * <p>A null field means "leave this alone", not "set it to null". Without
     * that distinction, an ordinary edit of, say, the price would clear every
     * per-item modifier override the owner had configured.
     */
    private void applyRequestedChanges(MenuItemModifierGroup link, MenuItemModifierGroupDTO requested) {
        if (requested.displayOrder() != null && requested.displayOrder() > 0) {
            link.setDisplayOrder(requested.displayOrder());
        }
        if (requested.requiredOverride() != null) {
            link.setRequiredOverride(requested.requiredOverride());
        }
        if (requested.isActive() != null) {
            link.setIsActive(requested.isActive());
        }
    }

    private void createLink(MenuItem item, MenuItemModifierGroupDTO requested, int positionInPayload) {
        ModifierGroup group = modifierGroupRepository.findById(requested.modifierGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ModifierGroup not found: " + requested.modifierGroupId()));

        // Defensive: the item's in-memory collection may not know about a link
        // written by another request since this item was loaded.
        if (linkRepository.existsByMenuItemIdAndModifierGroupId(item.getId(), group.getId())) {
            return;
        }

        MenuItemModifierGroup link = new MenuItemModifierGroup();
        link.setMenuItem(item);
        link.setModifierGroup(group);
        link.setDisplayOrder(positionOf(requested, positionInPayload));
        link.setRequiredOverride(requested.requiredOverride());
        link.setIsActive(requested.isActive() == null || requested.isActive());
        linkRepository.save(link);
    }

    /**
     * The position a newly created link should take.
     *
     * <p>Positions are 1-based, like every other {@code display_order} in the
     * schema. Null or 0 means "no position was chosen", so the group is
     * appended in the order the client listed it. (Before this, every link
     * defaulted to 0 and the UI silently fell back to ordering by id.)
     */
    private int positionOf(MenuItemModifierGroupDTO requested, int fallbackPosition) {
        return requested.displayOrder() != null && requested.displayOrder() > 0
                ? requested.displayOrder()
                : fallbackPosition;
    }

    /**
     * The item's current links, keyed by modifier group id.
     *
     * <p>A plain {@code toMap} throws {@code IllegalStateException} on a
     * duplicate key and would surface as an unexplained 500; the merge function
     * keeps the first entry instead.
     */
    private Map<Long, MenuItemModifierGroup> indexByGroupId(List<MenuItemModifierGroup> links) {
        if (links == null || links.isEmpty()) {
            return Map.of();
        }
        return links.stream().collect(Collectors.toMap(
                link -> link.getModifierGroup().getId(),
                Function.identity(),
                (first, duplicate) -> first));
    }
}