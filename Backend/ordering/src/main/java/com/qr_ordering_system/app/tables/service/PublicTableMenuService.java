package com.qr_ordering_system.app.tables.service;

import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.menu.dto.response.CustomerMenuCategoryResponse;
import com.qr_ordering_system.app.menu.service.CustomerMenuService;
import com.qr_ordering_system.app.tables.dto.response.PublicTableMenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The public "scan QR → see menu" flow (Phase 3).
 *
 * <h2>Why this class is NOT @Transactional - the sharpest edge in the flow</h2>
 *
 * The slug lookup MUST run with no tenant in context (see
 * {@link PublicTableResolver}). Everything after it runs as the table's
 * tenant: {@code menuForBranch} relies on the {@code @TenantId} filter -
 * notably {@code findAllActiveForCustomerMenu()} carries no explicit tenant
 * constraint - so the tenant must be in the thread-local BEFORE
 * {@code CustomerMenuService} opens its transaction and Hibernate pins the
 * session. A @Transactional on this method would open that session too
 * early, on the root tenant, and the menu would silently serve EVERY
 * tenant's items.
 *
 * So the order is exactly the one the JWT filter uses for staff requests:
 * resolve who the caller is, set the context, run the work, clear.
 */
@Service
@RequiredArgsConstructor
public class PublicTableMenuService {

    private final PublicTableResolver publicTableResolver;
    private final CustomerMenuService customerMenuService;

    /**
     * @throws com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException
     *         for an unknown slug, a closed branch, or a non-serving tenant -
     *         the same generic 404 for all three (see
     *         {@link PublicTableResolver} for the privacy contract).
     */
    public PublicTableMenuResponse menuForSlug(String qrSlug) {
        PublicTableResolver.PublicTable resolved = publicTableResolver.resolve(qrSlug);

        // Pin BEFORE CustomerMenuService's session opens - see class javadoc.
        TenantContextHolder.setTenantId(resolved.table().getTenantId());
        try {
            List<CustomerMenuCategoryResponse> categories =
                    customerMenuService.menuForBranch(resolved.branch().getId());

            return new PublicTableMenuResponse(
                    resolved.tenant().getBusinessName(),
                    resolved.tenant().getAddress(),
                    resolved.branch().getName(),
                    resolved.table().getTableNumber(),
                    categories);
        } finally {
            // Whoever sets a thread-local clears it: pooled threads outlive
            // the request and must not carry a tenant into the next one.
            TenantContextHolder.clear();
        }
    }
}
