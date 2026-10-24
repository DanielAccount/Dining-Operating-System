package com.qr_ordering_system.app.tables;

import com.qr_ordering_system.app.common.TenantScopedTestSupport;
import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.response.CategoryResponse;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.dto.response.PublicTableMenuResponse;
import com.qr_ordering_system.app.tables.service.PublicTableMenuService;
import com.qr_ordering_system.app.tables.service.TableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The public "scan QR -> menu" flow, against the real database.
 *
 * The one thing this suite must never stop proving is the tenant pinning:
 * {@code CustomerMenuService.findAllActiveForCustomerMenu()} carries no
 * explicit tenant constraint, so if {@code PublicTableMenuService} ever
 * fails to set the context before the menu transaction opens, a customer
 * would see EVERY tenant's dishes. The isolation test below fails loudly
 * on exactly that regression.
 *
 * These tests also mirror the real request shape deliberately: the service
 * is called with no tenant in the thread-local, exactly like an anonymous
 * phone request - never inside {@code inTenant}, which would hide the bug
 * this suite exists to catch.
 */
class PublicTableMenuIntegrationTest extends TenantScopedTestSupport {

    private static final BigDecimal PRICE = new BigDecimal("2.50");

    @Autowired
    private PublicTableMenuService publicTableMenuService;

    @Autowired
    private TableService tableService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private MenuItemService menuItemService;

    @Test
    @DisplayName("a slug serves its own tenant's menu - and only that tenant's")
    void slugServesItsOwnTenantsMenuOnly() {
        Long tenantA = tenantId();
        Long branchA = branch(tenantA, "Branch A", BranchStatus.ACTIVE);
        String slugA = table(tenantA, branchA, "T1");
        categoryWithItem(tenantA, "Coffee A", "Latte A");

        Long tenantB = createTenant("second");
        Long branchB = branch(tenantB, "Branch B", BranchStatus.ACTIVE);
        String slugB = table(tenantB, branchB, "T9");
        categoryWithItem(tenantB, "Coffee B", "Latte B");

        // No tenant in context here - an anonymous phone request.
        PublicTableMenuResponse menuA = publicTableMenuService.menuForSlug(slugA);

        assertEquals(NAME_PREFIX + "primary", menuA.businessName());
        assertEquals("Branch A", menuA.branchName());
        assertEquals("T1", menuA.tableNumber());

        // One category, one item, and they are tenant A's. If the pinning
        // regressed to the root tenant, "Coffee B" / "Latte B" would appear
        // here too - and the dev seed data with it.
        assertEquals(1, menuA.categories().size());
        assertEquals("Coffee A", menuA.categories().get(0).localizedNames().get("en"));
        assertEquals(1, menuA.categories().get(0).items().size());
        assertEquals("Latte A", menuA.categories().get(0).items().get(0).localizedNames().get("en"));

        PublicTableMenuResponse menuB = publicTableMenuService.menuForSlug(slugB);
        assertEquals("Branch B", menuB.branchName());
        assertEquals(1, menuB.categories().size());
        assertEquals("Latte B", menuB.categories().get(0).items().get(0).localizedNames().get("en"));

        // The service cleans up after itself: no tenant may linger on the
        // pooled test thread - the same thread the next request will use.
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("an unknown slug is a 404 with nothing revealed")
    void unknownSlugIs404() {
        assertThrows(ResourceNotFoundException.class,
                () -> publicTableMenuService.menuForSlug("zzNotARealSlug"));
    }

    @Test
    @DisplayName("a temporarily closed branch stops serving its QR codes")
    void closedBranchIs404() {
        Long tenant = tenantId();
        Long branchId = branch(tenant, "Closed For Renovation", BranchStatus.TEMPORARILY_CLOSED);
        String slug = table(tenant, branchId, "T1");
        categoryWithItem(tenant, "Coffee", "Latte");

        assertThrows(ResourceNotFoundException.class,
                () -> publicTableMenuService.menuForSlug(slug));
    }

    @Test
    @DisplayName("a suspended tenant stops serving, without confirming it to the customer")
    void suspendedTenantIs404() {
        Long tenant = tenantId();
        Long branchId = branch(tenant, "Branch", BranchStatus.ACTIVE);
        String slug = table(tenant, branchId, "T1");
        categoryWithItem(tenant, "Coffee", "Latte");

        // Tenant is not tenant-scoped itself, so no context is needed here.
        // save() is explicit because findById outside a transaction returns a
        // detached entity - mutating it alone would persist nothing.
        tenantRepository.findById(tenant).ifPresent(t -> {
            t.setStatus(TenantStatus.SUSPENDED);
            tenantRepository.save(t);
        });

        assertThrows(ResourceNotFoundException.class,
                () -> publicTableMenuService.menuForSlug(slug));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Long branch(Long tenantId, String name, BranchStatus status) {
        return inTenant(tenantId, () -> {
            Branch branch = new Branch();
            branch.setName(name);
            branch.setStatus(status);
            return branchRepository.save(branch).getId();
        });
    }

    private String table(Long tenantId, Long branchId, String number) {
        return inTenant(tenantId, () ->
                tableService.create(branchId, new TableCreateRequest(number)).qrSlug());
    }

    private void categoryWithItem(Long tenantId, String categoryEn, String itemEn) {
        // Supplier<Void> rather than Runnable: the support class has no
        // (tenantId, Runnable) overload, and returning null keeps the
        // explicit-tenant form that this test always needs.
        inTenant(tenantId, () -> {
            CategoryResponse category = categoryService.create(
                    new CategoryCreateRequest(names(categoryEn), null));

            // Items are visible by default: menuForBranch treats a missing
            // branch override as available=true, so no BranchMenuItem row
            // is needed to make the item appear.
            menuItemService.create(new MenuItemCreateRequest(
                    category.id(), names(itemEn), emptyMap(),
                    PRICE, null, null, null, null));
            return null;
        });
    }
}
