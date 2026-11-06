package com.qr_ordering_system.app.kitchen;

import com.qr_ordering_system.app.common.TenantScopedTestSupport;
import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.domain.SessionState;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.kitchen.dto.response.KitchenItemResponse;
import com.qr_ordering_system.app.kitchen.service.KitchenDisplayService;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.ModifierGroupCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.ModifierOptionDTO;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import com.qr_ordering_system.app.menu.service.ModifierGroupService;
import com.qr_ordering_system.app.orders.dto.request.OrderCartLine;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderDetailResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.service.OrderStaffService;
import com.qr_ordering_system.app.orders.service.OrderSubmissionService;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.domain.TableSession;
import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import com.qr_ordering_system.app.tables.repository.TableSessionRepository;
import com.qr_ordering_system.app.tables.service.TableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 kitchen workflow: what appears on the board, which transitions
 * are legal, how the parent order follows independently progressing lines,
 * and where branch/tenant isolation holds.
 */
class KitchenDisplayServiceTest extends TenantScopedTestSupport {

    @Autowired private KitchenDisplayService kitchenDisplayService;
    @Autowired private OrderSubmissionService orderSubmissionService;
    @Autowired private OrderStaffService orderStaffService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TableService tableService;
    @Autowired private TableRepository tableRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private MenuItemService menuItemService;
    @Autowired private ModifierGroupService modifierGroupService;
    @Autowired private TableSessionRepository tableSessionRepository;

    private Long tenant;
    private Long branchId;
    private Long otherBranchId;
    private Long tableId;
    private Long foodItemId;
    private Long drinkItemId;

    @BeforeEach
    void setUpFixture() {
        tenant = tenantId();

        branchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Kitchen Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        otherBranchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Other Kitchen Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        tableId = inTenant(tenant, () ->
                tableService.create(branchId, new TableCreateRequest("K1")).id());

        Long categoryId = inTenant(tenant, () ->
                categoryService.create(new CategoryCreateRequest(names("Kitchen food"), null)).id());

        foodItemId = inTenant(tenant, () -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names("Burger"), emptyMap(), new BigDecimal("5000"),
                null, null, null, null)).id());

        drinkItemId = inTenant(tenant, () -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names("Tea"), emptyMap(), new BigDecimal("1500"),
                null, null, null, null)).id());
    }

    @Test
    @DisplayName("a pending order stays off the KDS until staff accepts it")
    void acceptedOrderEntersKitchen() {
        OrderResponse placed = placeOrder(List.of(new OrderCartLine(foodItemId, 1, null)), false);

        assertTrue(inTenant(tenant, () -> kitchenDisplayService.list(branchId)).isEmpty());

        OrderResponse accepted = inTenant(tenant, () -> orderStaffService.accept(branchId, placed.id()));
        assertEquals("ACCEPTED", accepted.status());

        List<KitchenItemResponse> kitchen = inTenant(tenant, () ->
                kitchenDisplayService.list(branchId));

        assertEquals(1, kitchen.size());
        KitchenItemResponse line = kitchen.get(0);
        assertEquals(placed.id(), line.orderId());
        assertEquals("PENDING", line.itemStatus());
        assertEquals("ACCEPTED", line.orderStatus());
        assertEquals(accepted.invoiceNumber(), line.invoiceNumber());
    }

    @Test
    @DisplayName("a line advances preparing -> ready -> served and the order follows it")
    void lineAdvancesAndCompletesOrder() {
        OrderResponse placed = placeOrder(List.of(new OrderCartLine(foodItemId, 1, null)), true);
        Long itemId = placed.items().get(0).id();

        KitchenItemResponse preparing = inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(branchId, itemId));
        assertEquals("PREPARING", preparing.itemStatus());
        assertEquals("PREPARING", preparing.orderStatus());

        // A repeated request is a safe retry, not a second transition.
        KitchenItemResponse preparingAgain = inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(branchId, itemId));
        assertEquals("PREPARING", preparingAgain.itemStatus());

        KitchenItemResponse ready = inTenant(tenant, () ->
                kitchenDisplayService.markReady(branchId, itemId));
        assertEquals("READY", ready.itemStatus());
        assertEquals("READY", ready.orderStatus());
        assertNotNull(ready.readyAt());

        KitchenItemResponse served = inTenant(tenant, () ->
                kitchenDisplayService.markServed(branchId, itemId));
        assertEquals("SERVED", served.itemStatus());
        assertEquals("COMPLETED", served.orderStatus());
        assertNotNull(served.servedAt());

        // SERVED work has left the active kitchen board.
        assertTrue(inTenant(tenant, () -> kitchenDisplayService.list(branchId)).isEmpty());

        OrderDetailResponse detail = inTenant(tenant, () ->
                orderStaffService.detail(branchId, placed.id()));
        assertEquals(
                List.of("PENDING", "ACCEPTED", "PREPARING", "READY", "COMPLETED"),
                detail.history().stream().map(entry -> entry.status()).toList());
    }

    @Test
    @DisplayName("a kitchen stage cannot be skipped")
    void stagesCannotBeSkipped() {
        OrderResponse placed = placeOrder(List.of(new OrderCartLine(foodItemId, 1, null)), true);
        Long itemId = placed.items().get(0).id();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> inTenant(tenant, () -> kitchenDisplayService.markReady(branchId, itemId)));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("PENDING"));
        assertTrue(ex.getReason().contains("PREPARING"));
    }

    @Test
    @DisplayName("the order stays preparing until every line is ready, then stays ready until every line is served")
    void parentOrderFollowsSlowestLiveLine() {
        OrderResponse placed = placeOrder(List.of(
                new OrderCartLine(foodItemId, 1, null),
                new OrderCartLine(drinkItemId, 1, null)), true);
        Long foodLineId = placed.items().get(0).id();
        Long drinkLineId = placed.items().get(1).id();

        assertEquals("PREPARING", inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(branchId, foodLineId)).orderStatus());
        assertEquals("PREPARING", inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(branchId, drinkLineId)).orderStatus());

        assertEquals("PREPARING", inTenant(tenant, () ->
                kitchenDisplayService.markReady(branchId, foodLineId)).orderStatus());
        assertEquals("READY", inTenant(tenant, () ->
                kitchenDisplayService.markReady(branchId, drinkLineId)).orderStatus());

        assertEquals("READY", inTenant(tenant, () ->
                kitchenDisplayService.markServed(branchId, foodLineId)).orderStatus());
        assertEquals("COMPLETED", inTenant(tenant, () ->
                kitchenDisplayService.markServed(branchId, drinkLineId)).orderStatus());
    }

    @Test
    @DisplayName("a ticket remains readable and actionable after its menu item is retired")
    void retiredMenuItemDoesNotBreakTheKitchenBoard() {
        OrderResponse placed = placeOrder(
                List.of(new OrderCartLine(foodItemId, 1, null)), true);

        // Menus are soft-deleted when a dish retires. Historical order lines
        // keep their own name/price snapshots and must remain on the KDS.
        jdbcTemplate.update(
                "UPDATE menu_items SET is_deleted = TRUE WHERE tenant_id = ? AND id = ?",
                tenant, foodItemId);

        List<KitchenItemResponse> kitchen = inTenant(tenant, () ->
                kitchenDisplayService.list(branchId));

        assertEquals(1, kitchen.size());
        assertEquals(placed.items().get(0).id(), kitchen.get(0).id());
        assertEquals("Burger", kitchen.get(0).itemNameSnapshot().get("en"));

        // Updating the historical line must preserve its original menu FK;
        // the retired menu is represented by the row's own snapshots.
        KitchenItemResponse preparing = inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(branchId, kitchen.get(0).id()));
        assertEquals("PREPARING", preparing.itemStatus());
    }

    @Test
    @DisplayName("a ticket remains readable after its table is retired")
    void retiredTableDoesNotBreakTheKitchenBoard() {
        OrderResponse placed = placeOrder(
                List.of(new OrderCartLine(foodItemId, 1, null)), true);

        // Deleting a table must not make its historical orders unreadable.
        // The order's table_number_snapshot is the durable display value.
        inTenant(tenant, () -> {
            tableService.delete(branchId, tableId);
            return null;
        });

        List<KitchenItemResponse> kitchen = inTenant(tenant, () ->
                kitchenDisplayService.list(branchId));

        assertEquals(1, kitchen.size());
        assertEquals(placed.items().get(0).id(), kitchen.get(0).id());
        assertEquals("K1", kitchen.get(0).tableNumber());

        OrderDetailResponse detail = inTenant(tenant, () ->
                orderStaffService.detail(branchId, placed.id()));
        assertEquals("K1", detail.order().tableNumber());

        // Phase 6 will close/clean up sessions. Updating a session whose table
        // is retired must preserve its immutable historical table_id.
        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT table_session_id FROM orders WHERE tenant_id = ? AND id = ?",
                Long.class, tenant, placed.id());
        inTenant(tenant, () -> {
            TableSession session = tableSessionRepository.findById(sessionId).orElseThrow();
            session.setSessionState(SessionState.AWAITING_CLEANUP);
            tableSessionRepository.saveAndFlush(session);
            return null;
        });
        Long retainedTableId = jdbcTemplate.queryForObject(
                "SELECT table_id FROM table_sessions WHERE tenant_id = ? AND id = ?",
                Long.class, tenant, sessionId);
        assertEquals(tableId, retainedTableId);
    }

    @Test
    @DisplayName("a ticket remains readable and actionable after its modifier option is retired")
    void retiredModifierOptionDoesNotBreakTheKitchenBoard() {
        OrderResponse placed = placeOrder(
                List.of(new OrderCartLine(foodItemId, 1, null)), true);
        Long itemId = placed.items().get(0).id();

        Long optionId = inTenant(tenant, () -> modifierGroupService.create(
                new ModifierGroupCreateRequest(
                        names("Extras"), false, 0, 1,
                        List.of(new ModifierOptionDTO(
                                null, names("Cheese"), new BigDecimal("500"), true)),
                        true)).options().get(0).id());

        // Model a historical line that selected this option. The immutable
        // modifier snapshots are what the kitchen must continue to display.
        jdbcTemplate.update(
                "INSERT INTO order_item_modifiers " +
                        "(id, tenant_id, order_item_id, modifier_option_id, " +
                        "modifier_name, modifier_price, created_at) " +
                        "VALUES (nextval('entity_id_seq'), ?, ?, ?, ?, ?, now())",
                tenant, itemId, optionId, "Cheese", new BigDecimal("500"));
        jdbcTemplate.update(
                "UPDATE modifier_options SET is_deleted = TRUE WHERE tenant_id = ? AND id = ?",
                tenant, optionId);

        List<KitchenItemResponse> kitchen = inTenant(tenant, () ->
                kitchenDisplayService.list(branchId));

        assertEquals(1, kitchen.size());
        assertEquals(1, kitchen.get(0).modifiers().size());
        assertEquals("Cheese", kitchen.get(0).modifiers().get(0).name());

        KitchenItemResponse preparing = inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(branchId, itemId));
        assertEquals("PREPARING", preparing.itemStatus());

        Long retainedOptionId = jdbcTemplate.queryForObject(
                "SELECT modifier_option_id FROM order_item_modifiers " +
                        "WHERE tenant_id = ? AND order_item_id = ?",
                Long.class, tenant, itemId);
        assertEquals(optionId, retainedOptionId);
    }

    @Test
    @DisplayName("an unaccepted or cancelled order cannot be processed by the kitchen")
    void kitchenRequiresAnAcceptedLiveOrder() {
        OrderResponse pending = placeOrder(
                List.of(new OrderCartLine(foodItemId, 1, null)), false);
        Long pendingItemId = pending.items().get(0).id();

        ResponseStatusException pendingEx = assertThrows(ResponseStatusException.class,
                () -> inTenant(tenant, () ->
                        kitchenDisplayService.markPreparing(branchId, pendingItemId)));
        assertEquals(HttpStatus.CONFLICT.value(), pendingEx.getStatusCode().value());
        assertTrue(pendingEx.getReason().contains("not been accepted"));

        OrderResponse cancelled = placeOrder(
                List.of(new OrderCartLine(foodItemId, 1, null)), true);
        inTenant(tenant, () -> orderStaffService.cancel(
                branchId, cancelled.id(), "guest left", 7L));

        ResponseStatusException cancelledEx = assertThrows(ResponseStatusException.class,
                () -> inTenant(tenant, () -> kitchenDisplayService.markPreparing(
                        branchId, cancelled.items().get(0).id())));
        assertEquals(HttpStatus.CONFLICT.value(), cancelledEx.getStatusCode().value());
        assertTrue(cancelledEx.getReason().contains("cancelled"));
    }

    @Test
    @DisplayName("a line from another branch or tenant is a 404, never a 403")
    void kitchenActionsAreBranchAndTenantScoped() {
        OrderResponse primary = placeOrder(
                List.of(new OrderCartLine(foodItemId, 1, null)), true);
        Long primaryItemId = primary.items().get(0).id();

        assertThrows(ResourceNotFoundException.class, () -> inTenant(tenant, () ->
                kitchenDisplayService.markPreparing(otherBranchId, primaryItemId)));

        Long otherTenant = createTenant("other-kitchen");
        Long otherTenantBranch = inTenant(otherTenant, () -> {
            Branch branch = new Branch();
            branch.setName("Foreign kitchen");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        assertThrows(ResourceNotFoundException.class, () -> inTenant(tenant, () ->
                kitchenDisplayService.list(otherTenantBranch)));
    }

    /** Places an order through the normal engine, optionally accepting it. */
    private OrderResponse placeOrder(List<OrderCartLine> lines, boolean accept) {
        return inTenant(tenant, () -> {
            Branch branch = branchRepository.findById(branchId).orElseThrow();
            TableEntity table = tableRepository.findById(tableId).orElseThrow();
            OrderResponse placed = orderSubmissionService.submit(
                    branch, table, OrderSource.QR_CUSTOMER, null,
                    new OrderSubmissionRequest(
                            "kitchen-" + System.nanoTime(), lines, null, null));

            if (!accept) {
                return placed;
            }
            return orderStaffService.accept(branchId, placed.id());
        });
    }
}
