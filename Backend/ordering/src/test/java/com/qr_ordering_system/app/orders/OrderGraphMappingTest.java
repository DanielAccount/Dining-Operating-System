package com.qr_ordering_system.app.orders;

import com.qr_ordering_system.app.common.TenantScopedTestSupport;
import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.domain.OrderType;
import com.qr_ordering_system.app.common.domain.SessionState;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import com.qr_ordering_system.app.orders.domain.Order;
import com.qr_ordering_system.app.orders.domain.OrderItem;
import com.qr_ordering_system.app.orders.domain.OrderItemModifier;
import com.qr_ordering_system.app.orders.domain.OrderStatusHistory;
import com.qr_ordering_system.app.orders.repository.OrderItemRepository;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.orders.repository.OrderStatusHistoryRepository;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.domain.TableSession;
import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.dto.response.TableResponse;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import com.qr_ordering_system.app.tables.repository.TableSessionRepository;
import com.qr_ordering_system.app.tables.service.TableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4-M1's gate: the order entities map cleanly onto the V4/V5 schema and
 * a full graph (session -> order -> items -> modifier snapshots -> history)
 * persists and reads back - before any submission service logic exists.
 *
 * <p>What this deliberately proves, in one sweep:</p>
 * <ul>
 *   <li>the V5 CHECK constraints are satisfiable through the entity model
 *       (DINE_IN-requires-session, the money formula, quantity > 0)</li>
 *   <li>cascade PERSIST reaches items and their modifiers</li>
 *   <li>the new audit base binds tenant_id and leaves created_by null for
 *       anonymous-context inserts (the QR customer case)</li>
 *   <li>the denormalized branchId column, the JSONB name snapshot and the
 *       EAGER association chains hydrate correctly</li>
 *   <li>another tenant sees none of it</li>
 * </ul>
 */
class OrderGraphMappingTest extends TenantScopedTestSupport {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderStatusHistoryRepository orderHistoryRepository;
    @Autowired private TableSessionRepository tableSessionRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private TableService tableService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private MenuItemService menuItemService;
    @Autowired private MenuItemRepository menuItemRepository;

    @Test
    @DisplayName("a full order graph persists with cascades, snapshots and tenant binding")
    void fullOrderGraphPersists() {
        Long tenant = tenantId();

        Long branchId = inTenant(tenant, () -> {
            Branch branch = new Branch();
            branch.setName("Mapping Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            return branchRepository.save(branch).getId();
        });

        Long tableId = inTenant(tenant, () ->
                tableService.create(branchId, new TableCreateRequest("T1")).id());

        Long itemId = inTenant(tenant, () -> {
            Long categoryId = categoryService.create(
                    new CategoryCreateRequest(names("Drinks"), null)).id();

            return menuItemService.create(new MenuItemCreateRequest(
                    categoryId, names("Latte"), emptyMap(),
                    new BigDecimal("2000"), null, null, null, null)).id();
        });

        String idempotencyKey = "mapping-" + System.nanoTime();

        Long orderId = inTenant(tenant, () -> {
            Branch branch = branchRepository.findById(branchId).orElseThrow();
            TableEntity table = tableRepository.findById(tableId).orElseThrow();
            MenuItem menuItem = menuItemRepository.findById(itemId).orElseThrow();

            TableSession session = new TableSession();
            session.setBranch(branch);
            session.setTable(table);
            session.setSessionCode("S-" + System.nanoTime());
            session.setSessionState(SessionState.CUSTOMER_ORDERING);
            session.setOpenedAt(OffsetDateTime.now());
            tableSessionRepository.save(session);

            Order order = new Order();
            order.setBranch(branch);
            order.setTableSession(session);          // DINE_IN requires it (DB CHECK)
            order.setOrderType(OrderType.DINE_IN);
            order.setSource(OrderSource.QR_CUSTOMER);
            order.setTableNumberSnapshot("T1");
            order.setIdempotencyKey(idempotencyKey);
            // The money formula CHECK: final = subtotal - discount + tax + service.
            order.setSubtotal(new BigDecimal("4500"));
            order.setFinalAmount(new BigDecimal("4500"));

            OrderItem line = new OrderItem();
            line.setOrder(order);
            line.setBranchId(branchId);              // denormalized KDS column
            line.setMenuItem(menuItem);
            line.setItemNameSnapshot(Map.of("en", "Latte", "mm", "လက်တေ"));
            line.setQuantity(2);
            line.setPriceAtTimeOfOrder(new BigDecimal("2000"));
            line.setLineTotal(new BigDecimal("4000"));

            // Snapshot-only modifier row: a null option is the legal
            // "option was retired later" state and must persist fine.
            OrderItemModifier oatMilk = new OrderItemModifier();
            oatMilk.setOrderItem(line);
            oatMilk.setModifierOption(null);
            oatMilk.setModifierName("Oat milk");
            oatMilk.setModifierPrice(new BigDecimal("500"));
            line.getSelectedModifiers().add(oatMilk);

            OrderItem plain = new OrderItem();
            plain.setOrder(order);
            plain.setBranchId(branchId);
            plain.setMenuItem(menuItem);
            plain.setItemNameSnapshot(Map.of("en", "Espresso"));
            plain.setQuantity(1);
            plain.setPriceAtTimeOfOrder(new BigDecimal("500"));
            plain.setLineTotal(new BigDecimal("500"));

            order.getItems().add(line);
            order.getItems().add(plain);

            Order saved = orderRepository.save(order); // cascade: items -> modifiers

            OrderStatusHistory placed = new OrderStatusHistory();
            placed.setOrder(saved);
            placed.setStatus(OrderStatus.PENDING);
            placed.setRemarks("mapping test");
            orderHistoryRepository.save(placed);

            return saved.getId();
        });

        // ── Read back in a fresh persistence context ────────────────────
        inTenant(tenant, () -> {
            Order loaded = orderRepository.findById(orderId).orElseThrow();
            assertEquals(0, new BigDecimal("4500").compareTo(loaded.getSubtotal()));
            assertEquals(0, new BigDecimal("4500").compareTo(loaded.getFinalAmount()));
            assertTrue(loaded.getTableSession().isOrderable());
            assertEquals("T1", loaded.getTableSession().getTable().getTableNumber());

            List<OrderItem> lines = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
            assertEquals(2, lines.size());
            assertEquals(branchId, lines.get(0).getBranchId());

            OrderItem withModifier = lines.get(0);
            assertEquals("Latte", withModifier.getItemNameSnapshot().get("en"));
            assertEquals(1, withModifier.getSelectedModifiers().size());
            OrderItemModifier modifier = withModifier.getSelectedModifiers().get(0);
            assertEquals("Oat milk", modifier.getModifierName());
            // The audit base bound tenant_id on insert - NOT NULL would have
            // failed loudly otherwise.
            assertNotNull(modifier.getTenantId());
            // Anonymous context (a QR customer): auditing leaves created_by null.
            assertNull(modifier.getCreatedBy());

            List<OrderStatusHistory> timeline =
                    orderHistoryRepository.findByOrderIdOrderByIdAsc(orderId);
            assertEquals(1, timeline.size());
            assertEquals(OrderStatus.PENDING, timeline.get(0).getStatus());
            assertNotNull(timeline.get(0).getTenantId());

            // Idempotency lookup (the M2 replay path) finds the order.
            assertTrue(orderRepository.findByIdempotencyKey(idempotencyKey).isPresent());

            return null;
        });

        // ── Tenant isolation: another tenant sees none of it ────────────
        Long other = createTenant("second");
        inTenant(other, () -> {
            assertTrue(orderRepository.findById(orderId).isEmpty());
            assertTrue(orderRepository.findByIdempotencyKey(idempotencyKey).isEmpty());
            return null;
        });
    }
}
