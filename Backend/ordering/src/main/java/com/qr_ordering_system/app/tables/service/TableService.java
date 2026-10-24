package com.qr_ordering_system.app.tables.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.BranchAccessResolver;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.dto.mapper.TableMapper;
import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.dto.request.TableUpdateRequest;
import com.qr_ordering_system.app.tables.dto.response.TableResponse;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CRUD for a branch's restaurant tables (Phase 3).
 *
 * <p>Branch scoping follows the house pattern: every method resolves the
 * branch through {@link BranchAccessResolver}, so branch-pinned staff
 * (waiter, cashier, manager) are welded to their own branch regardless of
 * what they pass in the path, and the tenant-wide owner must name one
 * explicitly. A table that belongs to another branch is a 404, not a 403.</p>
 *
 * <p>Table numbers are compared case-insensitively: "t1" and "T1" on two
 * tents would send the same table's orders to two different screens, so the
 * service treats them as the same number and answers a friendly 409.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TableService {

    private final TableRepository tableRepository;
    private final BranchAccessResolver branchAccessResolver;
    private final QrSlugGenerator qrSlugGenerator;

    /**
     * Where the customer storefront lives. The API stamps
     * {@code {base-url}/t/{qrSlug}} into every response so the frontend
     * never assembles QR URLs itself.
     */
    @Value("${app.public.base-url}")
    private String publicBaseUrl;

    /** Every table of the branch, ordered by table number for the list screen. */
    @Transactional(readOnly = true)
    public List<TableResponse> list(Long branchId) {
        Branch branch = branchAccessResolver.resolve(branchId);

        return tableRepository.findAllByBranchIdOrderByTableNumberAsc(branch.getId())
                .stream()
                .map(table -> TableMapper.toResponse(table, publicBaseUrl))
                .toList();
    }

    /**
     * Creates a table together with its first QR slug. The slug is minted
     * server-side (never accepted from the client) and stays with the table
     * across renames, so printed codes survive a number edit.
     */
    public TableResponse create(Long branchId, TableCreateRequest request) {
        Branch branch = branchAccessResolver.resolve(branchId);
        String tableNumber = request.tableNumber().trim();

        requireFreeNumber(branch.getId(), tableNumber, null);

        TableEntity table = new TableEntity();
        table.setBranch(branch);
        table.setTableNumber(tableNumber);
        table.setQrSlug(qrSlugGenerator.generateUnique());

        return TableMapper.toResponse(tableRepository.save(table), publicBaseUrl);
    }

    /**
     * Renames a table. The slug is deliberately untouched: the printed code
     * keeps working. (Regenerating is a separate, explicit action.)
     */
    public TableResponse update(Long branchId, Long tableId, TableUpdateRequest request) {
        Branch branch = branchAccessResolver.resolve(branchId);
        TableEntity table = requireTable(branch.getId(), tableId);

        String newNumber = request.tableNumber().trim();
        requireFreeNumber(branch.getId(), newNumber, table.getId());

        table.setTableNumber(newNumber);
        return TableMapper.toResponse(table, publicBaseUrl);
    }

    /**
     * Soft-deletes a table (the house {@code @SoftDelete} on BaseEntity).
     * Both unique indexes are partial ({@code WHERE is_deleted = FALSE}),
     * so the number and the slug are immediately free for reuse - while the
     * row itself survives for Phase 4, when order history will still point
     * at "which table was this".
     */
    public void delete(Long branchId, Long tableId) {
        Branch branch = branchAccessResolver.resolve(branchId);
        TableEntity table = requireTable(branch.getId(), tableId);

        tableRepository.delete(table);
    }

    /**
     * Mints a NEW slug for the table, which invalidates every previously
     * printed code - old stickers lead nowhere. The UI must confirm; this
     * method is the consequence, not the question.
     */
    public TableResponse regenerateQrSlug(Long branchId, Long tableId) {
        Branch branch = branchAccessResolver.resolve(branchId);
        TableEntity table = requireTable(branch.getId(), tableId);

        table.setQrSlug(qrSlugGenerator.generateUnique());
        return TableMapper.toResponse(table, publicBaseUrl);
    }

    // ── helpers ────────────────────────────────────────────────────────

    /**
     * 409 before the DB ever sees it - the same friendly-pre-check policy
     * as the menu module. {@code excludingId} is the row being renamed, so
     * saving a table under its own number is not a conflict with itself.
     */
    private void requireFreeNumber(Long branchId, String tableNumber, Long excludingId) {
        boolean taken = excludingId == null
                ? tableRepository.existsByBranchIdAndTableNumberIgnoreCase(branchId, tableNumber)
                : tableRepository.existsByBranchIdAndTableNumberIgnoreCaseAndIdNot(branchId, tableNumber, excludingId);

        if (taken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Table number '" + tableNumber + "' already exists in this branch");
        }
    }

    private TableEntity requireTable(Long branchId, Long tableId) {
        return tableRepository.findByIdAndBranchId(tableId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));
    }
}
