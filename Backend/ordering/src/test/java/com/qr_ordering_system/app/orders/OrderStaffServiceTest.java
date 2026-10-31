package com.qr_ordering_system.app.orders;

import com.qr_ordering_system.app.common.TenantScopedTestSupport;
import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import com.qr_ordering_system.app.orders.dto.request.OrderCartLine;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderDetailResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.orders.service.OrderStaffService;
import com.qr_ordering_system.app.orders.service.OrderSubmissionService;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.repository.TableRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staff-side order surface (Phase 4, M3): waiter-created orders, the
 * branch-scoped list/detail, and the two transitions staff own - accept
 * (with invoice minting) and cancel (with who/when/why).
 */
class OrderStaffServiceTest extends TenantScopedTestSupport {

    @Autowired private OrderStaffService orderStaffService;
    @Autowired private OrderSubmissionService orderSubmissionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TableService tableService;
    @Autowired private TableRepository tableRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private MenuItemService menuItemService;

    private Long tenant;
    private Long branchId;
    private Long otherBranchId;
    private Long tableId;
    private Long itemId;

    @BeforeEach
    void setUpFixture() {
        tenant = tenantId();

        branchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Staff Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        otherBranchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Other Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        tableId = inTenant(tenant, () ->
                tableService.create(branchId, new TableCreateRequest("T1")).id());

        Long categoryId = inTenant(tenant, () ->
                categoryService.create(new CategoryCreateRequest(names("Drinks"), null)).id());

        itemId = inTenant(tenant, () -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names("Latte"), emptyMap(), new BigDecimal("2000"),
                null, null, null, null)).id());
    }

    /** Place an order through the engine (independent of the code under test). */
    private OrderResponse placeOrder() {
        return inTenant(tenant, () -> {
            Branch branch = branchRepository.findById(branchId).orElseThrow();
            TableEntity table = tableRepository.findById(tableId).orElseThrow();
            return orderSubmissionService.submit(branch, table, OrderSource.QR_CUSTOMER, null,
                    new OrderSubmissionRequest(
                            "staff-" + System.nanoTime(), List.of(new OrderCartLine(itemId, 1, null)),
                            null, null));
        });
    }

    @Test
    @DisplayName("a waiter-placed order records source, placer and the guest's table")
    void staffCreatePlacesWaiterOrder() {
        OrderResponse order = inTenant(tenant, () -> orderStaffService.createAtTable(
                branchId, tableId,
                new OrderSubmissionRequest(
                        "waiter-" + System.nanoTime(), List.of(new OrderCartLine(itemId, 2, null)),
                        "birthday table", null),
                OrderSource.WAITER, 42L));

        assertEquals("PENDING", order.status());
        assertEquals("WAITER", order.source());
        assertEquals(42L, placedByOf(order));
        assertEquals("birthday table", order.customerNote());
    }

    @Test
    @DisplayName("accepting a PENDING order mints the invoice number and appends ACCEPTED history")
    void acceptMintsInvoiceAndRecordsHistory() {
        OrderResponse placed = placeOrder();

        OrderResponse accepted = inTenant(tenant, () ->
                orderStaffService.accept(branchId, placed.id()));

        assertEquals("ACCEPTED", accepted.status());
        assertEquals("INV-%06d".formatted(placed.id()), accepted.invoiceNumber());

        OrderDetailResponse detail = inTenant(tenant, () ->
                orderStaffService.detail(branchId, placed.id()));
        assertEquals(2, detail.history().size());
        assertEquals("PENDING", detail.history().get(0).status());
        assertEquals("ACCEPTED", detail.history().get(1).status());

        // Accepting twice is a conflict, not a no-op.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> inTenant(tenant, () -> orderStaffService.accept(branchId, placed.id())));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
    }

    @Test
    @DisplayName("cancelling records who, when and why - and only once")
    void cancelRecordsReasonAndBy() {
        OrderResponse placed = placeOrder();

        OrderResponse cancelled = inTenant(tenant, () ->
                orderStaffService.cancel(branchId, placed.id(), "Guest left before ordering more", 7L));

        assertEquals("CANCELLED", cancelled.status());

        OrderDetailResponse detail = inTenant(tenant, () ->
                orderStaffService.detail(branchId, placed.id()));
        assertEquals(2, detail.history().size());
        assertEquals("CANCELLED", detail.history().get(1).status());
        assertTrue(detail.history().get(1).remarks().contains("Guest left"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> inTenant(tenant, () ->
                        orderStaffService.cancel(branchId, placed.id(), "twice", 7L)));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
    }

    @Test
    @DisplayName("a cancelled order cannot be accepted, and an accepted one can still be cancelled")
    void transitionsRespectEachOther() {
        OrderResponse placed = placeOrder();

        inTenant(tenant, () -> orderStaffService.cancel(branchId, placed.id(), null, 7L));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> inTenant(tenant, () -> orderStaffService.accept(branchId, placed.id())));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());

        // The other direction stays legal: accepted -> cancelled (kitchen
        // about to cook the wrong thing, etc.).
        OrderResponse second = placeOrder();
        inTenant(tenant, () -> orderStaffService.accept(branchId, second.id()));
        OrderResponse cancelledAccepted = inTenant(tenant, () ->
                orderStaffService.cancel(branchId, second.id(), "wrong table", 7L));
        assertEquals("CANCELLED", cancelledAccepted.status());
    }

    @Test
    @DisplayName("an order from another branch is a 404, never a 403")
    void orderFromAnotherBranchIs404() {
        OrderResponse placed = placeOrder();

        assertThrows(ResourceNotFoundException.class, () -> inTenant(tenant, () ->
                orderStaffService.accept(otherBranchId, placed.id())));
        assertThrows(ResourceNotFoundException.class, () -> inTenant(tenant, () ->
                orderStaffService.detail(otherBranchId, placed.id())));
    }

    /** Reads the placed_by column directly from the persisted order. */
    private Long placedByOf(OrderResponse order) {
        return inTenant(tenant, () ->
                orderRepository.findById(order.id()).orElseThrow().getPlacedBy());
    }
}
