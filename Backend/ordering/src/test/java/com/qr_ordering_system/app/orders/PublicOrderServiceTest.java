package com.qr_ordering_system.app.orders;

import com.qr_ordering_system.app.common.TenantScopedTestSupport;
import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import com.qr_ordering_system.app.orders.dto.request.OrderCartLine;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.orders.service.PublicOrderService;
import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.dto.response.TableResponse;
import com.qr_ordering_system.app.tables.repository.TableSessionRepository;
import com.qr_ordering_system.app.tables.service.TableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public order flow as a phone experiences it (Phase 4, M3).
 *
 * <p>Every submission in this suite happens on the RAW TEST THREAD - no
 * {@code inTenant} wrapper, no tenant anywhere in context - because that is
 * precisely what an anonymous scan is. The whole point is proving the
 * service pins the tenant from the slug before the engine's transaction
 * opens: if that pinning ever regresses, the slug lookup or the submit
 * fails loudly here instead of leaking across tenants.</p>
 */
class PublicOrderServiceTest extends TenantScopedTestSupport {

    @Autowired private PublicOrderService publicOrderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TableSessionRepository tableSessionRepository;
    @Autowired private TableService tableService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private MenuItemService menuItemService;

    private Long tenant;
    private Long branchId;
    private TableResponse table;
    private Long itemId;

    @BeforeEach
    void setUpFixture() {
        tenant = tenantId();

        branchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Public Order Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        table = inTenant(tenant, () -> tableService.create(branchId, new TableCreateRequest("T1")));

        Long categoryId = inTenant(tenant, () ->
                categoryService.create(new CategoryCreateRequest(names("Drinks"), null)).id());

        itemId = inTenant(tenant, () -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names("Latte"), emptyMap(), new BigDecimal("2000"),
                null, null, null, null)).id());
    }

    private OrderCartLine latte(int quantity) {
        return new OrderCartLine(itemId, quantity, null);
    }

    @Test
    @DisplayName("an anonymous scan-and-submit places the order - tenant pinned from the slug alone")
    void anonymousSubmissionThroughSlugPlacesOrder() {
        // No tenant context anywhere: the raw thread, exactly like a phone.
        OrderResponse order = publicOrderService.submit(table.qrSlug(),
                new OrderSubmissionRequest("pub-1-" + System.nanoTime(), List.of(latte(1)), null, null));

        assertEquals("PENDING", order.status());
        assertEquals("T1", order.tableNumber());
        assertNotNull(order.sessionCode());

        // It really landed in the resolved tenant's rows.
        inTenant(tenant, () -> {
            assertTrue(orderRepository.findById(order.id()).isPresent());
            assertEquals(1, tableSessionRepository.count());
            return null;
        });
    }

    @Test
    @DisplayName("the same idempotency key replays through the public path too")
    void replayThroughPublicPath() {
        String key = "pub-replay-" + System.nanoTime();
        OrderResponse first = publicOrderService.submit(table.qrSlug(),
                new OrderSubmissionRequest(key, List.of(latte(1)), null, null));
        OrderResponse second = publicOrderService.submit(table.qrSlug(),
                new OrderSubmissionRequest(key, List.of(latte(1)), null, null));

        assertEquals(first.id(), second.id());
        inTenant(tenant, () -> {
            assertEquals(1, orderRepository.count());
            return null;
        });
    }

    @Test
    @DisplayName("a returning phone re-finds the table's orders by session code")
    void myOrdersReattachBySessionCode() {
        OrderResponse placed = publicOrderService.submit(table.qrSlug(),
                new OrderSubmissionRequest("pub-reattach-" + System.nanoTime(), List.of(latte(2)), null, null));

        List<OrderResponse> mine = publicOrderService.orders(table.qrSlug(), placed.sessionCode());
        assertEquals(1, mine.size());
        assertEquals(placed.id(), mine.get(0).id());

        // Unknown code: empty list, not an error - a fresh phone has no
        // orders yet, and the code is unguessable so nothing leaks.
        assertEquals(0, publicOrderService.orders(table.qrSlug(), "zzBogusCode").size());
        assertEquals(0, publicOrderService.orders(table.qrSlug(), null).size());
    }

    @Test
    @DisplayName("a dead slug answers the same generic 404 as the menu endpoint")
    void deadSlugIs404() {
        assertThrows(ResourceNotFoundException.class, () -> publicOrderService.submit(
                "zzNotASlug",
                new OrderSubmissionRequest("pub-dead-" + System.nanoTime(), List.of(latte(1)), null, null)));
    }

    @Test
    @DisplayName("a closed branch stops taking orders, without confirming it to the customer")
    void closedBranchIs404() {
        inTenant(tenant, () -> {
            Branch branch = branchRepository.findById(branchId).orElseThrow();
            branch.setStatus(BranchStatus.TEMPORARILY_CLOSED);
            branchRepository.save(branch);
            return null;
        });

        assertThrows(ResourceNotFoundException.class, () -> publicOrderService.submit(
                table.qrSlug(),
                new OrderSubmissionRequest("pub-closed-" + System.nanoTime(), List.of(latte(1)), null, null)));
    }
}
