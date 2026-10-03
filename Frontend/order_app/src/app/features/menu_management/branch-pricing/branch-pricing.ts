import { ChangeDetectorRef, Component, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BranchContextService } from '../../../core/branch/branch-context.service';
import { BranchPricingService } from './branch_pricing_service';
// Imported from its definition rather than re-exported through
// branch_pricing_model: one hop is fewer for the template type-checker to lose
// track of, and the row shape is the single source of truth either way.
import { BranchMenuItemRow } from '../models/products_model';
import { extractErrorMessage } from '../../../shared/http/error-message';

/**
 * Editable view-model for one grid row.
 * priceInput: '' means "no override / follow base price".
 */
interface PricingRow extends BranchMenuItemRow {
  priceInput: string;
  dirty: boolean;
}

@Component({
  selector: 'app-branch-pricing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './branch-pricing.html',
})
export class BranchPricingComponent implements OnInit {

  private readonly branchPricingService = inject(BranchPricingService);
  private readonly branchContext = inject(BranchContextService);
  private readonly cdr = inject(ChangeDetectorRef);

  // The active branch lives in BranchContextService, which the sidebar picker
  // also writes to. This component used to keep a private copy and fetch the
  // branch list itself, so switching branch in the navbar and opening this screen
  // could disagree about which branch was being edited.
  readonly branches = this.branchContext.branches;
  readonly selectedBranchId = this.branchContext.selectedBranchId;
  readonly isTenantWide = this.branchContext.isTenantWide;

  // Data
  rows = signal<PricingRow[]>([]);

  // UI state
  loading = signal<boolean>(false);
  saving = signal<boolean>(false);
  error = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  readonly dirtyRows = signal<PricingRow[]>([]);
  readonly hasChanges = signal<boolean>(false);

  /**
   * Guards against a slow response for branch A overwriting rows already loaded
   * for branch B after the user switched.
   */
  private loadToken = 0;

  constructor() {
    // Rows follow the active branch, whichever screen changed it.
    effect(() => {
      const branchId = this.selectedBranchId();
      untracked(() => this.loadRows(branchId));
    });
  }

  ngOnInit(): void {
    // No load() call: BranchContextService reacts to the signed-in user itself,
    // which is what makes logout/login-as-another-tenant correct.
    this.syncDirtyFlags();
  }

  // ---- Branch / row loading ----

  selectBranch(branchId: number | null): void {
    this.branchContext.select(branchId);
  }

  loadRows(branchId: number | null): void {
    const token = ++this.loadToken;

    if (branchId == null) {
      this.rows.set([]);
      this.syncDirtyFlags();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.branchPricingService.getRows(branchId).subscribe({
      next: (data) => {
        if (token !== this.loadToken) return; // superseded by a newer load
        this.rows.set(data.map(r => this.toPricingRow(r)));
        this.loading.set(false);
        this.syncDirtyFlags();
        this.cdr.detectChanges();
      },
      error: (err) => {
        if (token !== this.loadToken) return;
        this.loading.set(false);
        this.rows.set([]);
        this.syncDirtyFlags();
        this.error.set(extractErrorMessage(err, 'Failed to load branch menu items.'));
        console.error('Failed to load branch menu items', err);
      }
    });
  }

  // ---- Editing ----

  onPriceInput(row: PricingRow, value: string): void {
    this.patchRow(row.menuItemId, { priceInput: value, dirty: true });
  }

  onAvailabilityToggle(row: PricingRow, available: boolean): void {
    this.patchRow(row.menuItemId, { isAvailable: available, dirty: true });
    // Availability flips feel immediate — save this single row right away.
    this.saveRow({ ...row, isAvailable: available, dirty: true });
  }

  /**
   * Stock is per branch: marking an item sold out here affects only this
   * branch's customer menu. Like availability it saves immediately, because it
   * is a decision staff make mid-service.
   */
  onStockToggle(row: PricingRow, inStock: boolean): void {
    this.patchRow(row.menuItemId, { isInStock: inStock, dirty: true });
    this.saveRow({ ...row, isInStock: inStock, dirty: true });
  }

  isPriceInvalid(row: PricingRow): boolean {
    if (row.priceInput.trim() === '') return false; // empty = no override
    const n = Number(row.priceInput);
    return isNaN(n) || n < 0;
  }

  // ---- Saving ----

  saveRow(row: PricingRow): void {
    if (this.isPriceInvalid(row) || this.selectedBranchId() == null) return;

    this.saving.set(true);
    this.error.set(null);

    this.branchPricingService.upsert(this.selectedBranchId()!, row.menuItemId, {
      priceOverride: this.parsePrice(row.priceInput),
      isAvailable: row.isAvailable,
      isInStock: row.isInStock
    }).subscribe({
      next: (updated) => {
        this.replaceRow(row.menuItemId, this.toPricingRow(updated));
        this.saving.set(false);
        this.flashSuccess('Saved.');
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(extractErrorMessage(err, 'Failed to save item. Please try again.'));
        console.error('Failed to save branch menu item', err);
      }
    });
  }

  saveAll(): void {
    const branchId = this.selectedBranchId();
    const dirty = this.dirtyRows().filter(r => !this.isPriceInvalid(r));
    if (branchId == null || dirty.length === 0) return;

    this.saving.set(true);
    this.error.set(null);

    this.branchPricingService.bulkUpsert(branchId, {
      items: dirty.map(r => ({
        menuItemId: r.menuItemId,
        priceOverride: this.parsePrice(r.priceInput),
        isAvailable: r.isAvailable,
        isInStock: r.isInStock
      }))
    }).subscribe({
      next: (updated) => {
        const byId = new Map(updated.map(u => [u.menuItemId, u]));
        this.rows.set(this.rows().map(r => {
          const u = byId.get(r.menuItemId);
          return u ? this.toPricingRow(u) : r;
        }));
        this.saving.set(false);
        this.flashSuccess(`Saved ${updated.length} item(s).`);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(extractErrorMessage(err, 'Failed to save changes. Please try again.'));
        console.error('Failed to bulk save branch menu items', err);
      }
    });
  }

  resetRow(row: PricingRow): void {
    const branchId = this.selectedBranchId();
    if (branchId == null) return;

    // No backend row exists yet — just clear local edits.
    if (row.branchMenuItemId == null) {
      this.patchRow(row.menuItemId, {
        priceInput: '', priceOverride: null, effectivePrice: row.basePrice,
        isAvailable: true, isInStock: true, isOrderable: row.itemIsActive, dirty: false
      });
      return;
    }

    this.error.set(null);
    this.branchPricingService.reset(branchId, row.menuItemId).subscribe({
      next: () => {
        this.patchRow(row.menuItemId, {
          branchMenuItemId: null, priceInput: '', priceOverride: null,
          effectivePrice: row.basePrice, isAvailable: true, isInStock: true,
          isOrderable: row.itemIsActive, dirty: false
        });
        this.flashSuccess('Reset to global defaults.');
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error.set(extractErrorMessage(err, 'Failed to reset item.'));
        console.error('Failed to reset branch menu item', err);
      }
    });
  }

  // ---- Helpers ----

  /** Immutable row updates so the rows() signal emits a new array. */
  private patchRow(menuItemId: number, patch: Partial<PricingRow>): void {
    this.rows.update(rows =>
      rows.map(r => r.menuItemId === menuItemId ? { ...r, ...patch } : r)
    );
    this.syncDirtyFlags();
  }

  /**
   * dirtyRows/hasChanges used to be computed()s derived from rows(). They are
   * plain signals now so they can be refreshed from one place - after every
   * row edit, load, reset and save - instead of relying on each of those paths
   * remembering to keep the derivation in step.
   */
  private syncDirtyFlags(): void {
    const dirty = this.rows().filter(r => r.dirty);
    this.dirtyRows.set(dirty);
    this.hasChanges.set(dirty.length > 0);
  }

  private replaceRow(menuItemId: number, replacement: PricingRow): void {
    this.rows.update(rows =>
      rows.map(r => r.menuItemId === menuItemId ? replacement : r)
    );
    this.syncDirtyFlags();
  }

  private toPricingRow(r: BranchMenuItemRow): PricingRow {
    return {
      ...r,
      priceInput: r.priceOverride != null ? String(r.priceOverride) : '',
      dirty: false
    };
  }

  private parsePrice(input: string): number | null {
    const trimmed = input.trim();
    return trimmed === '' ? null : Number(trimmed);
  }

  private flashSuccess(message: string): void {
    this.successMessage.set(message);
    setTimeout(() => this.successMessage.set(null), 2500);
  }

  formatPrice(price: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(price);
  }
}
