package com.qr_ordering_system.app.tables.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A physical restaurant table and the QR code that identifies it.
 *
 * The QR code printed on the table encodes
 * {@code {app.public.base-url}/t/{qrSlug}} - the public customer storefront
 * route, reserved since the frontend route restructure. Scanning it is how
 * a customer reaches the menu; no login, no roles.
 *
 * Deliberately minimal, per the ERD and the V4 migration: no capacity, no
 * zone, no status. A table's operational state ("is a customer sitting
 * there right now") is {@code table_sessions} work, which is Phase 4 and
 * must not leak into Phase 3. Until then, "active" simply means not deleted.
 *
 * Uniqueness is the database's job, guarded by two partial unique indexes
 * from V4 (both {@code WHERE is_deleted = FALSE}):
 *   uk_table_branch_number (branch_id, table_number) - a branch's numbers
 *   uk_table_qr_slug       (qr_slug)                - global, across tenants
 */
@Entity
@Table(name = "restaurant_tables", indexes = {
        @Index(name = "idx_table_branch", columnList = "branch_id")
})
@Getter @Setter
public class TableEntity extends AbstractTenantEntity {

    /**
     * The branch this table sits in. Branch-pinned staff see only their
     * branch's tables; the tenant-wide owner switches branches in the UI.
     *
     * EAGER is required, not a choice: Hibernate 7 refuses LAZY to-one
     * associations to @SoftDelete entities (Branch extends BaseEntity),
     * because a lazy proxy cannot apply the soft-delete filter at join
     * time. See the same comment on BranchMenuItem.branch.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "table_number", nullable = false, length = 64)
    private String tableNumber;

    /**
     * Random, URL-safe, globally unique - see {@code QrSlugGenerator} for why
     * it is random rather than derived from tenant/branch/table names.
     *
     * Updatable on purpose: the regenerate-qr endpoint mints a replacement
     * when a printed code is compromised or a table is re-stickered.
     */
    @Column(name = "qr_slug", nullable = false, length = 120)
    private String qrSlug;
}
