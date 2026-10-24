package com.qr_ordering_system.app.tables.repository;

import com.qr_ordering_system.app.tables.domain.TableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepository extends JpaRepository<TableEntity, Long> {

    /** Tables of one branch, stable order for the management list screen. */
    List<TableEntity> findAllByBranchIdOrderByTableNumberAsc(Long branchId);

    /**
     * Table lookup scoped to a branch. A table id that belongs to another
     * branch is a 404, not a 403 - the caller must not learn that the id
     * exists somewhere else. Same policy as every other branch-scoped repo.
     */
    Optional<TableEntity> findByIdAndBranchId(Long id, Long branchId);

    /**
     * Natural-key pre-check for uk_table_branch_number, so the service can
     * answer a friendly 409 instead of letting the DB index throw
     * (see the menu module's "Key things for Junior.md", rule 4).
     */
    boolean existsByBranchIdAndTableNumberIgnoreCase(Long branchId, String tableNumber);

    /** Same check, excluding the row currently being renamed. */
    boolean existsByBranchIdAndTableNumberIgnoreCaseAndIdNot(Long branchId, String tableNumber, Long id);

    /**
     * Lookup by QR slug for the public storefront menu endpoint (Phase 3,
     * Milestone 2). See {@code QrSlugGenerator} and the controller notes:
     * the caller is an unauthenticated customer, so this MUST run without a
     * tenant in TenantContextHolder. With no context the resolver reports
     * SYSTEM_TENANT_ID (root => no tenant filter), and the globally unique
     * index uk_table_qr_slug makes this a single-row lookup across tenants.
     */
    Optional<TableEntity> findByQrSlug(String qrSlug);

    /**
     * Uniqueness pre-check used while minting a slug (uk_table_qr_slug).
     * NOTE: like every derived query on a @TenantId entity, this is filtered
     * by the current tenant when called from a staff request. That is fine -
     * it catches the realistic collision class (retries inside one tenant).
     * Cross-tenant collisions are the DB index's job and surface as a 409
     * through the DataIntegrityViolation handler.
     */
    boolean existsByQrSlug(String qrSlug);
}
