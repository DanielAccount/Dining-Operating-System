package com.qr_ordering_system.app.orders;

import com.qr_ordering_system.app.common.TenantScopedTestSupport;
import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.domain.OrderType;
import com.qr_ordering_system.app.common.domain.SessionState;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.menu.domain.BranchMenuItem;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemModifierGroupDTO;
import com.qr_ordering_system.app.menu.dto.request.ModifierGroupCreateRequest;
import com.qr_ordering_system.app.menu.dto.response.ModifierGroupResponse;
import com.qr_ordering_system.app.menu.repository.BranchMenuItemRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import com.qr_ordering_system.app.menu.service.ModifierGroupService;
import com.qr_ordering_system.app.orders.dto.request.OrderCartLine;
import com.qr_ordering_system.app.orders.dto.request.OrderCartModifier;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order submission engine's contract (Phase 4, M2). Every rule the
 * service promises has a test that fails the day someone breaks it:
 *
 * <ul>
 *   <li>same idempotency key = same order - even after the menu changed</li>
 *   <li>availability is judged at submit time, and one bad line rejects
 *       the whole cart without side effects (no order, no session)</li>
 *   <li>pricing and snapshots are server-only</li>
 *   <li>sessions open lazily and are reused; awaiting-cleanup tables say so</li>
 * </ul>
 */
class OrderSubmissionServiceTest extends TenantScopedTestSupport {

    @Autowired private OrderSubmissionService orderSubmissionService;
    @Autowired private TableSessionRepository tableSessionRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private TableService tableService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private MenuItemService menuItemService;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private ModifierGroupService modifierGroupService;
    @Autowired private BranchMenuItemRepository branchMenuItemRepository;
    @Autowired private com.qr_ordering_system.app.orders.repository.OrderRepository orderRepository;
    @Autowired private com.qr_ordering_system.app.orders.repository.OrderStatusHistoryRepository orderStatusHistoryRepository;

    private Long tenant;
    private Long branchId;
    private Long tableId;
    private Long latteId;
    private Long espressoId;
    private Long largeOptionId;

    @BeforeEach
    void setUpFixture() {
        tenant = tenantId();

        branchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Engine Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        tableId = inTenant(tenant, () ->
                tableService.create(branchId, new TableCreateRequest("T1")).id());

        Long categoryId = inTenant(tenant, () ->
                categoryService.create(new CategoryCreateRequest(names("Drinks"), null)).id());

        // "Size" group with one available option, linked to the latte.
        ModifierGroupResponse size = inTenant(tenant, () -> modifierGroupService.create(
                new ModifierGroupCreateRequest(
                        names("Size"), true, 1, 1,
                        List.of(new com.qr_ordering_system.app.menu.dto.request.ModifierOptionDTO(
                                null,
                                names("Large"), new BigDecimal("500"), true)),
                        true)));;
        largeOptionId = size.options().get(0).id();

        latteId = inTenant(tenant, () -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names("Latte"), emptyMap(), new BigDecimal("2000"),
                null, null, null,
                List.of(new MenuItemModifierGroupDTO(size.id(), 1, true, true)))).id());

        espressoId = inTenant(tenant, () -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names("Espresso"), emptyMap(), new BigDecimal("500"),
                null, null, null, null)).id());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static OrderCartLine line(Long itemId, int quantity, Long... optionIds) {
        List<OrderCartModifier> modifiers = optionIds.length == 0
                ? null
                : Arrays.stream(optionIds).map(OrderCartModifier::new).toList();
        return new OrderCartLine(itemId, quantity, modifiers);
    }

    private OrderResponse submit(String key, boolean withTable, OrderCartLine... lines) {
        return inTenant(tenant, () -> {
            Branch branch = branchRepository.findById(branchId).orElseThrow();
            TableEntity table = withTable
                    ? tableRepository.findById(tableId).orElseThrow()
                    : null;
            return orderSubmissionService.submit(
                    branch, table, OrderSource.QR_CUSTOMER, null,
                    new OrderSubmissionRequest(key, List.of(lines), null, null));
        });
    }

    private long orderCount() {
        return inTenant(tenant, () -> orderRepository.count());
    }

    private long sessionCount() {
        return inTenant(tenant, () -> tableSessionRepository.count());
    }

    private void overrideAvailability(Long itemId, Boolean available, Boolean inStock) {
        inTenant(tenant, () -> {
            BranchMenuItem bmi = branchMenuItemRepository
                    .findByBranchIdAndMenuItemId(branchId, itemId)
                    .orElseGet(BranchMenuItem::new);
            bmi.setBranch(branchRepository.findById(branchId).orElseThrow());
            bmi.setMenuItem(menuItemRepository.findById(itemId).orElseThrow());
            bmi.setIsAvailable(available);
            bmi.setIsInStock(inStock);
            branchMenuItemRepository.save(bmi);
            return null;
        });
    }

    // ── the contract ───────────────────────────────────────────────────

    @Test
    @DisplayName("a placed order: PENDING, priced server-side, snapshotted, session opened")
    void placesPendingOrderWithSnapshotsAndAmounts() {
        OrderResponse order = submit("key-happy-" + System.nanoTime(),
                true,
                line(latteId, 2, largeOptionId),   // 2 x (2000 + 500) = 5000
                line(espressoId, 1));              // 1 x 500           =  500

        assertEquals(OrderStatus.PENDING.name(), order.status());
        assertEquals(OrderType.DINE_IN.name(), order.orderType());
        assertEquals(OrderSource.QR_CUSTOMER.name(), order.source());
        assertEquals("T1", order.tableNumber());
        assertNotNull(order.sessionCode());

        // Server-side math, judged by the DB CHECK at insert time.
        assertEquals(0, new BigDecimal("5500").compareTo(order.subtotal()));
        assertEquals(0, new BigDecimal("5500").compareTo(order.finalAmount()));

        // Snapshots and modifier pricing.
        assertEquals(2, order.items().size());
        assertEquals("Latte", order.items().get(0).itemNameSnapshot().get("en"));
        assertEquals(0, new BigDecimal("2000").compareTo(order.items().get(0).unitPrice()));
        assertEquals(0, new BigDecimal("5000").compareTo(order.items().get(0).lineTotal()));
        assertEquals(1, order.items().get(0).modifiers().size());
        assertEquals("Large", order.items().get(0).modifiers().get(0).name());
        assertEquals(0, new BigDecimal("500").compareTo(order.items().get(0).modifiers().get(0).price()));

        // The session really opened, and the status story started.
        assertEquals(1, sessionCount());
        assertEquals(1, inTenant(tenant, () ->
                orderStatusHistoryRepository.findByOrderIdOrderByIdAsc(order.id()).size()));
        assertEquals(OrderStatus.PENDING.name(), inTenant(tenant, () ->
                orderStatusHistoryRepository.findByOrderIdOrderByIdAsc(order.id()).get(0).getStatus().name()));
    }

    @Test
    @DisplayName("the same idempotency key replays the original order - even after the menu changed")
    void sameKeyReplaysTheOriginalOrder() {
        String key = "key-replay-" + System.nanoTime();
        OrderResponse first = submit(key, true, line(latteId, 1));
        OrderResponse second = submit(key, true, line(latteId, 1));

        assertEquals(first.id(), second.id());
        assertEquals(1, orderCount());

        // The menu changed since (sold out) - replay is NOT re-validated.
        overrideAvailability(latteId, true, false);
        OrderResponse third = submit(key, true, line(latteId, 1));
        assertEquals(first.id(), third.id());
        assertEquals(1, orderCount());

        // And the key is tenant-scoped: another tenant sees no replay.
        Long other = createTenant("second");
        inTenant(other, () -> {
            assertTrue(orderRepository.findByIdempotencyKey(key).isEmpty());
            return null;
        });
    }

    @Test
    @DisplayName("a sold-out line rejects the whole cart, with no order and no session left behind")
    void soldOutLineRejectsTheWholeCart() {
        overrideAvailability(latteId, true, false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-soldout-" + System.nanoTime(), true,
                        line(latteId, 1), line(espressoId, 1)));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Latte"));
        assertTrue(ex.getReason().contains("sold out"));
        assertEquals(0, orderCount());
        assertEquals(0, sessionCount());
    }

    @Test
    @DisplayName("an item this branch stopped offering is 'no longer available', not 'sold out'")
    void branchHiddenLineRejectsWithHonestWording() {
        overrideAvailability(latteId, false, true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-hidden-" + System.nanoTime(), true, line(latteId, 1)));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("no longer available"));
        assertEquals(0, orderCount());
    }

    @Test
    @DisplayName("a retired dish rejects the cart even with no branch override at all")
    void retiredItemRejectsTheCart() {
        inTenant(tenant, () -> {
            menuItemRepository.findById(latteId).orElseThrow().setIsActive(false);
            return null;
        });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-retired-" + System.nanoTime(), true, line(latteId, 1)));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("no longer on the menu"));
    }

    @Test
    @DisplayName("a second order at the same table reuses the open session")
    void secondOrderReusesTheOpenSession() {
        OrderResponse first = submit("key-a-" + System.nanoTime(), true, line(espressoId, 1));
        OrderResponse second = submit("key-b-" + System.nanoTime(), true, line(espressoId, 1));

        assertEquals(first.sessionCode(), second.sessionCode());
        assertEquals(1, sessionCount());
    }

    @Test
    @DisplayName("a table awaiting cleanup tells new orders to ask staff, instead of a generic 409")
    void awaitingCleanupTableRejectsNewOrders() {
        submit("key-cleanup-1-" + System.nanoTime(), true, line(espressoId, 1));

        inTenant(tenant, () -> {
            TableSession session = tableSessionRepository
                    .findFirstByTableIdAndSessionStateNotOrderByIdDesc(tableId, SessionState.CLOSED)
                    .orElseThrow();
            session.setSessionState(SessionState.AWAITING_CLEANUP);
            tableSessionRepository.save(session);
            return null;
        });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-cleanup-2-" + System.nanoTime(), true, line(espressoId, 1)));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("being prepared"));
        assertEquals(1, orderCount()); // only the first one
    }

    @Test
    @DisplayName("a takeaway order skips the session entirely (and satisfies the DB CHECK)")
    void takeawayOrderSkipsTheSession() {
        OrderResponse takeaway = submit("key-takeaway-" + System.nanoTime(),
                false, line(latteId, 1));

        assertEquals(OrderType.TAKEAWAY.name(), takeaway.orderType());
        assertNull(takeaway.sessionCode());
        assertNull(takeaway.tableNumber());
        assertEquals(0, sessionCount());
        assertEquals(1, orderCount());
    }

    @Test
    @DisplayName("an unknown modifier option id is a 400, not a silent guess")
    void unknownModifierOptionIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-unknownopt-" + System.nanoTime(),
                        true, line(latteId, 1, 999_999_999L)));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Unknown modifier option"));
        assertEquals(0, orderCount());
    }

    @Test
    @DisplayName("an option from a group the item does not offer is rejected")
    void foreignModifierOptionIsRejected() {
        ModifierGroupResponse toppings = inTenant(tenant, () -> modifierGroupService.create(
                new ModifierGroupCreateRequest(
                        names("Toppings"), false, 0, 1,
                        List.of(new com.qr_ordering_system.app.menu.dto.request.ModifierOptionDTO(
                                null,
                                names("Cinnamon"), BigDecimal.ZERO, true)),
                        true)));;
        Long cinnamonId = toppings.options().get(0).id();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-foreignopt-" + System.nanoTime(),
                        true, line(latteId, 1, cinnamonId)));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("not offered"));
        assertEquals(0, orderCount());
    }

    @Test
    @DisplayName("an available-then-retired option rejects the line as a conflict")
    void unavailableModifierOptionIsRejected() {
        // A dedicated item whose only option is unavailable - simpler and more
        // honest than retrofitting the fixture item's links through an update.
        ModifierGroupResponse syrup = inTenant(tenant, () -> modifierGroupService.create(
                new ModifierGroupCreateRequest(
                        names("Syrup"), false, 0, 1,
                        List.of(new com.qr_ordering_system.app.menu.dto.request.ModifierOptionDTO(
                                null,
                                names("Vanilla"), BigDecimal.ZERO, false)),
                        true)));;

        Long mochaId = inTenant(tenant, () -> {
            Long categoryId = categoryService.create(
                    new CategoryCreateRequest(names("Mochas"), null)).id();
            return menuItemService.create(new MenuItemCreateRequest(
                    categoryId, names("Mocha"), emptyMap(), new BigDecimal("1500"),
                    null, null, null,
                    List.of(new MenuItemModifierGroupDTO(syrup.id(), 1, false, true)))).id();
        });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-deadopt-" + System.nanoTime(),
                        true, line(mochaId, 1, syrup.options().get(0).id())));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("no longer available"));
        assertEquals(0, orderCount());
    }

    @Test
    @DisplayName("an empty cart is a 400 before anything is touched")
    void emptyCartIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> submit("key-empty-" + System.nanoTime(), true));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("empty"));
        assertEquals(0, orderCount());
        assertEquals(0, sessionCount());
    }
}
