package com.qr_ordering_system.app.tables.service;

import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import com.qr_ordering_system.app.menu.dto.response.CustomerMenuCategoryResponse;
import com.qr_ordering_system.app.menu.service.CustomerMenuService;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.dto.response.PublicTableMenuResponse;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The public "scan QR → see menu" flow (Phase 3). No authentication, no
 * roles, no branch header - the QR slug is the only credential.
 *
 * <h2>Why this class is NOT @Transactional - the sharpest edge in the flow</h2>
 *
 * The slug lookup MUST run with no tenant in context: the caller is an
 * anonymous phone, so {@code HibernateTenantIdentifierResolver} reports
 * SYSTEM_TENANT_ID (root), Hibernate applies <em>no</em> tenant filter,
 * and the globally unique index {@code uk_table_qr_slug} makes it a
 * single-row lookup. That is the one query in the whole app that is
 * <em>allowed</em> to read cross-tenant.
 *
 * Everything after it runs as the table's tenant. {@code menuForBranch}
 * relies on the {@code @TenantId} filter - notably
 * {@code findAllActiveForCustomerMenu()} carries no explicit tenant
 * constraint - so the tenant must be in the thread-local BEFORE
 * {@code CustomerMenuService} opens its transaction and Hibernate pins
 * the session. A @Transactional on this method would open that session
 * too early, on the root tenant, and the menu would silently serve
 * EVERY tenant's items.
 *
 * So the order is exactly the one the JWT filter uses for staff requests:
 * resolve who the caller is, set the context, run the work, clear.
 */
@Service
@RequiredArgsConstructor
public class PublicTableMenuService {

    private final TableRepository tableRepository;
    private final CustomerMenuService customerMenuService;

    /**
     * @param qrSlug the slug from the printed QR code
     * @throws ResourceNotFoundException for an unknown slug, a closed
     * branch, or a non-serving tenant - deliberately the same generic
     * 404 for all three. A sticker on a table must not explain to a
     * customer (or a competitor) WHY it stopped working, and must not
     * confirm which business, branch or table it belonged to.
     */
    public PublicTableMenuResponse menuForSlug(String qrSlug) {
        // 1. The one sanctioned cross-tenant read in the app.
        TableEntity table = tableRepository.findByQrSlug(qrSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Menu not found"));

        // EAGER associations (see TableEntity.branch) - branch and tenant
        // arrive with the table on the same unfiltered session.
        Branch branch = table.getBranch();
        Tenant tenant = table.getTenant();

        // 2. Serving gate. TRIAL still serves: a customer should never see
        // billing problems. SUSPENDED / CANCELLED / TRIAL_EXPIRED do not.
        // (Phase 7 owns the full subscription policy; this is the floor.)
        if (branch == null || tenant == null
                || branch.getStatus() != BranchStatus.ACTIVE
                || !isServing(tenant.getStatus())) {
            throw new ResourceNotFoundException("Menu not found");
        }

        // 3. Pin BEFORE CustomerMenuService's session opens - see class javadoc.
        TenantContextHolder.setTenantId(table.getTenantId());
        try {
            List<CustomerMenuCategoryResponse> categories =
                    customerMenuService.menuForBranch(branch.getId());

            return new PublicTableMenuResponse(
                    tenant.getBusinessName(),
                    tenant.getAddress(),
                    branch.getName(),
                    table.getTableNumber(),
                    categories);
        } finally {
            // Whoever sets a thread-local clears it: pooled threads outlive
            // the request and must not carry a tenant into the next one.
            TenantContextHolder.clear();
        }
    }

    private boolean isServing(TenantStatus status) {
        return status == TenantStatus.ACTIVE || status == TenantStatus.TRIAL;
    }
}
