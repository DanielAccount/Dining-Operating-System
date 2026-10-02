package com.qr_ordering_system.app.menu;

import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.response.CategoryResponse;
import com.qr_ordering_system.app.menu.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tenant isolation for the bulk display-order UPDATEs.
 *
 * Reordering is done with {@code @Modifying} JPQL statements whose text carries
 * no tenant predicate. They are safe only because Hibernate adds one for
 * {@code @TenantId} entities. That assumption is invisible in the code and
 * nothing proved it: a regression would let one tenant renumber another
 * tenant's menu.
 *
 * So it is proved here, explicitly, with two real tenants.
 *
 * This class is also the standing warning about SYSTEM_TENANT_ID. Hibernate
 * treats tenant 0 as "root" and applies <em>no</em> filtering for it, so any code
 * path that reaches a tenant-scoped repository without a tenant in context reads
 * across every tenant. See {@code HibernateTenantIdentifierResolver}.
 */
class BulkUpdateTenantIsolationTest extends TenantScopedTestSupport {

    @Autowired
    private CategoryService categoryService;

    @Test
    @DisplayName("renumbering one tenant's categories leaves another tenant's untouched")
    void bulkShiftIsScopedToOneTenant() {
        Long tenantA = tenantId();
        Long tenantB = createTenant("second");

        List<Long> categoriesA = createCategories(tenantA, 3);
        List<Long> categoriesB = createCategories(tenantB, 3);

        assertEquals(List.of(1, 2, 3), sortedPositionsIn(tenantA, categoriesA));
        assertEquals(List.of(1, 2, 3), sortedPositionsIn(tenantB, categoriesB));

        // Tenant A inserts a category at the top. This is the bulk
        // CategoryRepository.shiftOrdersUp UPDATE - the statement under test.
        Long inserted = inTenant(tenantA, () -> categoryService.create(
                new CategoryCreateRequest(names("cat-insertion"), 1)).id());
        categoriesA.add(inserted);

        assertEquals(1, positionIn(tenantA, inserted),
                "the new category must land at the top");
        assertEquals(List.of(1, 2, 3, 4), sortedPositionsIn(tenantA, categoriesA),
                "tenant A's own sequence must stay dense - otherwise the shift never ran "
                        + "and the assertion below would pass for the wrong reason");

        assertEquals(List.of(1, 2, 3), sortedPositionsIn(tenantB, categoriesB),
                "the bulk UPDATE leaked across tenants");
    }

    @Test
    @DisplayName("a tenant cannot even read another tenant's categories")
    void readsAreScopedToOneTenant() {
        Long tenantA = tenantId();
        Long tenantB = createTenant("second");

        createCategories(tenantA, 2);
        List<Long> categoriesB = createCategories(tenantB, 2);

        List<Long> visibleToA = inTenant(tenantA, () -> categoryService.getAll().stream()
                .map(CategoryResponse::id)
                .toList());

        assertEquals(2, visibleToA.size());
        assertFalse(visibleToA.contains(categoriesB.get(0)),
                "tenant A saw one of tenant B's categories");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private List<Long> createCategories(Long tenantId, int count) {
        return inTenant(tenantId, () -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                ids.add(categoryService.create(
                        new CategoryCreateRequest(names("cat-" + i), null)).id());
            }
            return ids;
        });
    }

    /** Positions of the given categories, sorted - i.e. "is the sequence dense?". */
    private List<Integer> sortedPositionsIn(Long tenantId, List<Long> categoryIds) {
        return positionsIn(tenantId, categoryIds).stream().sorted().toList();
    }

    private List<Integer> positionsIn(Long tenantId, List<Long> categoryIds) {
        return inTenant(tenantId, () -> categoryIds.stream()
                .map(id -> categoryService.getById(id).displayOrder())
                .toList());
    }

    private Integer positionIn(Long tenantId, Long categoryId) {
        return inTenant(tenantId, () -> categoryService.getById(categoryId).displayOrder());
    }
}
