package com.qr_ordering_system.app.orders.service;

import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.domain.OrderType;
import com.qr_ordering_system.app.common.domain.SessionState;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.menu.domain.BranchMenuItem;
import com.qr_ordering_system.app.menu.domain.MenuAvailability;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierOption;
import com.qr_ordering_system.app.menu.repository.BranchMenuItemRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import com.qr_ordering_system.app.menu.repository.ModifierOptionRepository;
import com.qr_ordering_system.app.orders.domain.Order;
import com.qr_ordering_system.app.orders.domain.OrderItem;
import com.qr_ordering_system.app.orders.domain.OrderItemModifier;
import com.qr_ordering_system.app.orders.domain.OrderStatusHistory;
import com.qr_ordering_system.app.orders.dto.mapper.OrderMapper;
import com.qr_ordering_system.app.orders.dto.request.OrderCartLine;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.orders.repository.OrderStatusHistoryRepository;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.domain.TableSession;
import com.qr_ordering_system.app.tables.repository.TableSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * THE one path through which orders enter the system (Phase 4, M2).
 * Both the public QR flow and the waiter screen funnel through
 * {@link #submit} - the callers decide who is ordering (source, placedBy)
 * and where (branch, table); every other decision is made here.
 *
 * <p>The contract, in order of importance:</p>
 * <ul>
 *   <li><b>Idempotent replay</b>: the idempotency key is checked BEFORE
 *   anything else. "Same key = same order" means the phone's retry after a
 *   flaky signal replays the original result - it never re-validates a cart
 *   against a menu that may have changed since.</li>
 *
 *   <li><b>Availability re-checked at submit time</b>: the customer saw the
 *   menu minutes ago; the kitchen's reality is now. A line that retired, is
 *   not offered at this branch, or sold out rejects the WHOLE cart with a
 *   per-item reason - a partial order a customer never approved is worse
 *   than asking them to fix their cart.</li>
 *
 *   <li><b>Server-side pricing</b>: every amount is computed from the live
 *   menu (branch override, else base price; plus option extra prices) and
 *   snapshotted onto the order lines. The database CHECK on final_amount is
 *   the judge; no client ever sends a number.</li>
 *
 *   <li><b>Sessions open lazily</b>: the first order at a table creates the
 *   session (code minted and returned to the phone); later orders reuse
 *   it. A concurrent first-order race is settled by the
 *   {@code uk_table_session_open} index: one insert wins, the loser answers
 *   409 and the phone retries with the SAME idempotency key - which then
 *   replays cleanly once the winner commits.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderSubmissionService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderHistoryRepository;
    private final TableSessionRepository tableSessionRepository;
    private final MenuItemRepository menuItemRepository;
    private final BranchMenuItemRepository branchMenuItemRepository;
    private final ModifierOptionRepository modifierOptionRepository;
    private final SessionCodeGenerator sessionCodeGenerator;

    /**
     * @param table    the physical table; null means a TAKEAWAY order (the
     *                 DB CHECK requires exactly one of session / takeaway)
     * @param source   QR_CUSTOMER, WAITER or CASHIER - recorded, never trusted
     * @param placedBy staff user id, or null for anonymous QR customers
     */
    public OrderResponse submit(Branch branch,
                                TableEntity table,
                                OrderSource source,
                                Long placedBy,
                                OrderSubmissionRequest request) {

        // ── 1. Idempotency replay, before any validation ────────────────
        Optional<Order> existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return OrderMapper.toResponse(existing.get());
        }

        List<OrderCartLine> cart = request.items();
        if (cart == null || cart.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The order is empty - there is nothing to submit.");
        }

        // ── 2. Load the live menu state for exactly the cart's items ────
        Set<Long> itemIds = cart.stream().map(OrderCartLine::menuItemId).collect(Collectors.toSet());
        Map<Long, MenuItem> items = menuItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        Map<Long, BranchMenuItem> overrides = branchMenuItemRepository
                .findAllForBranchAndMenuItemIds(branch.getId(), itemIds).stream()
                .collect(Collectors.toMap(
                        bmi -> bmi.getMenuItem().getId(), Function.identity(), (a, b) -> a));

        // ── 3. Validate every line; reject the whole cart if any fails ─
        List<String> problems = new ArrayList<>();
        for (OrderCartLine line : cart) {
            MenuItem item = items.get(line.menuItemId());

            if (item == null || !Boolean.TRUE.equals(item.getIsActive())) {
                problems.add(nameOf(items.get(line.menuItemId()), line.menuItemId())
                        + " is no longer on the menu");
                continue;
            }

            BranchMenuItem override = overrides.get(line.menuItemId());
            boolean available = override == null || Boolean.TRUE.equals(override.getIsAvailable());
            boolean inStock = override == null || Boolean.TRUE.equals(override.getIsInStock());

            if (!MenuAvailability.isOrderable(item.getIsActive(), available, inStock)) {
                // Same honest distinction the storefront makes: "sold out"
                // means the branch ran out; anything invisible to customers
                // is "no longer available" instead.
                problems.add(nameOf(item, line.menuItemId())
                        + (MenuAvailability.isVisibleToCustomer(item.getIsActive(), available)
                            ? " is sold out right now"
                            : " is no longer available"));
            }
        }

        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your order was not placed: " + String.join("; ", problems)
                            + ". Please update your cart and try again.");
        }

        // ── 4. Validate modifier selections against the live menu ─────
        Map<Long, ModifierOption> options = loadOptions(cart);
        Map<Long, List<ModifierOption>> selectedByLine = new LinkedHashMap<>();
        for (OrderCartLine line : cart) {
            List<ModifierOption> selected = new ArrayList<>();
            if (line.modifiers() != null) {
                for (var pick : line.modifiers()) {
                    selected.add(requireSelectableOption(
                            items.get(line.menuItemId()), pick.modifierOptionId(), options));
                }
            }
            selectedByLine.put(line.menuItemId(), selected);
        }

        // ── 5. Session: reuse the table's open one, or open lazily ────
        // A table whose session is AWAITING_CLEANUP is being reset for the
        // next guests and must not take new orders - silently opening a
        // second session would trip uk_table_session_open anyway, but with
        // an unhelpful generic conflict instead of this honest message.
        TableSession session = null;
        OrderType orderType = OrderType.DINE_IN;
        if (table != null) {
            TableSession current = tableSessionRepository
                    .findFirstByTableIdAndSessionStateNotOrderByIdDesc(table.getId(), SessionState.CLOSED)
                    .orElse(null);

            if (current != null && current.getSessionState() == SessionState.AWAITING_CLEANUP) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This table is being prepared for the next guests - please ask our staff for help.");
            }

            session = current != null ? current : openSession(branch, table);
        } else {
            orderType = OrderType.TAKEAWAY;
        }

        // ── 6. Build the order, priced and snapshotted server-side ─────
        Order order = new Order();
        order.setBranch(branch);
        order.setTableSession(session);
        order.setOrderType(orderType);
        order.setSource(source);
        order.setPlacedBy(placedBy);
        order.setTableNumberSnapshot(table != null ? table.getTableNumber() : null);
        order.setCustomerNote(request.customerNote());
        order.setCustomerPhoneNumber(request.customerPhoneNumber());
        order.setIdempotencyKey(request.idempotencyKey());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderCartLine line : cart) {
            MenuItem item = items.get(line.menuItemId());
            BranchMenuItem override = overrides.get(line.menuItemId());

            BigDecimal unitPrice = override != null && override.getPriceOverride() != null
                    ? override.getPriceOverride()
                    : item.getBasePrice();

            List<ModifierOption> selected = selectedByLine.get(line.menuItemId());
            BigDecimal modifiersTotal = selected.stream()
                    .map(ModifierOption::getExtraPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setBranchId(branch.getId());
            orderItem.setMenuItem(item);
            orderItem.setItemNameSnapshot(new HashMap<>(item.getLocalizedNames()));
            orderItem.setQuantity(line.quantity());
            orderItem.setPriceAtTimeOfOrder(unitPrice);
            BigDecimal lineTotal = unitPrice
                    .add(modifiersTotal)
                    .multiply(BigDecimal.valueOf(line.quantity()));
            orderItem.setLineTotal(lineTotal);

            for (ModifierOption option : selected) {
                OrderItemModifier snapshot = new OrderItemModifier();
                snapshot.setOrderItem(orderItem);
                snapshot.setModifierOption(option);
                snapshot.setModifierName(nameOf(option.getLocalizedNames()));
                snapshot.setModifierPrice(option.getExtraPrice());
                orderItem.getSelectedModifiers().add(snapshot);
            }

            order.getItems().add(orderItem);
            subtotal = subtotal.add(lineTotal);
        }

        // The DB CHECK is the judge: final = subtotal - discount + tax + service.
        // Tax rates and service charges are configuration the product does
        // not have yet - Phase 8 territory - so Phase 4 orders are untaxed.
        order.setSubtotal(subtotal);
        order.setFinalAmount(subtotal);

        Order saved = orderRepository.save(order); // cascade persists items -> modifiers

        // ── 7. The status story starts here ────────────────────────────
        OrderStatusHistory placed = new OrderStatusHistory();
        placed.setOrder(saved);
        placed.setStatus(OrderStatus.PENDING);
        placed.setRemarks(source == OrderSource.QR_CUSTOMER
                ? "Placed by customer via QR"
                : "Placed by staff (" + source + ")");
        orderHistoryRepository.save(placed);

        return OrderMapper.toResponse(saved);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private TableSession openSession(Branch branch, TableEntity table) {
        TableSession session = new TableSession();
        session.setBranch(branch);
        session.setTable(table);
        session.setSessionCode(sessionCodeGenerator.generateFor(table.getId()));
        session.setSessionState(SessionState.CUSTOMER_ORDERING);
        session.setOpenedAt(java.time.OffsetDateTime.now());
        return tableSessionRepository.save(session);
    }

    private Map<Long, ModifierOption> loadOptions(List<OrderCartLine> cart) {
        Set<Long> optionIds = cart.stream()
                .filter(line -> line.modifiers() != null)
                .flatMap(line -> line.modifiers().stream())
                .map(pick -> pick.modifierOptionId())
                .collect(Collectors.toSet());

        if (optionIds.isEmpty()) {
            return Map.of();
        }

        return modifierOptionRepository.findAllById(optionIds).stream()
                .collect(Collectors.toMap(ModifierOption::getId, Function.identity()));
    }

    /**
     * An option is selectable on a line only if it belongs to one of the
     * item's ACTIVE modifier groups (active link, active group) and is
     * itself available. The customer menu showed exactly this set, so a
     * mismatch means either a stale cart or a tampered request.
     */
    private ModifierOption requireSelectableOption(MenuItem item, Long optionId,
                                                   Map<Long, ModifierOption> options) {
        ModifierOption option = options.get(optionId);
        if (option == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown modifier option: " + optionId);
        }

        boolean offeredOnItem = item.getModifierGroups().stream()
                .filter(link -> Boolean.TRUE.equals(link.getIsActive()))
                .map(MenuItemModifierGroup::getModifierGroup)
                .filter(group -> Boolean.TRUE.equals(group.getIsActive()))
                .anyMatch(group -> group.getId().equals(option.getModifierGroup().getId()));

        if (!offeredOnItem) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Modifier '" + nameOf(option.getLocalizedNames())
                            + "' is not offered on '" + nameOf(item.getLocalizedNames()) + "'");
        }

        if (!Boolean.TRUE.equals(option.getIsAvailable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Modifier '" + nameOf(option.getLocalizedNames()) + "' is no longer available");
        }

        return option;
    }

    /** English first, then the first available value - same rule as the storefront. */
    private String nameOf(MenuItem item, Long fallbackId) {
        return item != null ? nameOf(item.getLocalizedNames()) : "item " + fallbackId;
    }

    private String nameOf(Map<String, String> localizedNames) {
        if (localizedNames == null || localizedNames.isEmpty()) {
            return "";
        }
        return localizedNames.getOrDefault("en", localizedNames.values().iterator().next());
    }
}
