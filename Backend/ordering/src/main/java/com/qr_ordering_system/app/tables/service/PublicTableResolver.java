package com.qr_ordering_system.app.tables.service;

import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves a printed QR slug for an anonymous caller: table, branch and
 * tenant, with the serving gates applied. Shared by every
 * {@code /api/public/...} endpoint - the menu (Phase 3) and the order
 * endpoints (Phase 4) - because duplicating the tenant-isolation check is
 * how it ends up applied to one endpoint and forgotten on the next.
 *
 * <p>The lookup MUST run with no tenant in context: public callers are
 * anonymous, so {@code HibernateTenantIdentifierResolver} reports
 * SYSTEM_TENANT_ID (root), Hibernate applies <em>no</em> tenant filter, and
 * the globally unique index {@code uk_table_qr_slug} makes it a single-row
 * lookup. That is the one query in the app allowed to read cross-tenant -
 * callers then pin the resolved tenant before any further work.</p>
 *
 * <p>Every failure is the same generic 404 ("Menu not found") on purpose:
 * a dead sticker on a table must not reveal which business, branch or table
 * it belonged to, nor WHY it stopped working (closed branch, suspended
 * tenant, unknown slug - indistinguishable).</p>
 */
@Component
@RequiredArgsConstructor
public class PublicTableResolver {

    /** The resolved bundle a public endpoint needs before pinning its tenant. */
    public record PublicTable(TableEntity table, Branch branch, Tenant tenant) {}

    private final TableRepository tableRepository;

    public PublicTable resolve(String qrSlug) {
        TableEntity table = tableRepository.findByQrSlug(qrSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Menu not found"));

        // EAGER associations (see TableEntity.branch) - branch and tenant
        // arrive with the table on the same unfiltered session.
        Branch branch = table.getBranch();
        Tenant tenant = table.getTenant();

        // Serving gates. TRIAL still serves: a customer should never see
        // billing problems. SUSPENDED / CANCELLED / TRIAL_EXPIRED do not.
        // (Phase 7 owns the full subscription policy; this is the floor.)
        if (branch == null || tenant == null
                || branch.getStatus() != BranchStatus.ACTIVE
                || !isServing(tenant.getStatus())) {
            throw new ResourceNotFoundException("Menu not found");
        }

        return new PublicTable(table, branch, tenant);
    }

    private boolean isServing(TenantStatus status) {
        return status == TenantStatus.ACTIVE || status == TenantStatus.TRIAL;
    }
}
