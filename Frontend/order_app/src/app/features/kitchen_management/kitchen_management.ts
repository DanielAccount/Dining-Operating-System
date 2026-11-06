import { CommonModule } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { BranchContextService } from '../../core/branch/branch-context.service';
import { extractErrorMessage } from '../../shared/http/error-message';
import { KitchenService } from './kitchen.service';
import { KitchenItem, KitchenItemStatus, KitchenTicket } from './kitchen.models';

/**
 * Route: /app/kitchen (permission: KDS_VIEW)
 *
 * The live Phase 5 kitchen board. Accepted order lines are polled every ten
 * seconds and each line advances independently:
 * PENDING -> PREPARING -> READY -> SERVED.
 */
@Component({
  selector: 'app-kitchen-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './kitchen_management.html',
})
export class KitchenManagement implements OnInit, OnDestroy {
  private readonly kitchenService = inject(KitchenService);
  private readonly branchContext = inject(BranchContextService);
  private readonly auth = inject(AuthService);

  readonly branches = this.branchContext.branches;
  readonly selectedBranchId = this.branchContext.selectedBranchId;
  readonly isTenantWide = this.branchContext.isTenantWide;

  readonly items = signal<KitchenItem[]>([]);
  readonly loading = signal(false);
  readonly refreshing = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);
  readonly updatingIds = signal<ReadonlySet<number>>(new Set<number>());

  readonly canUpdate = computed(() =>
    this.auth.hasPermission('KDS_UPDATE_ITEM_STATUS'),
  );

  /** Lines grouped back into tickets for a kitchen-friendly presentation. */
  readonly tickets = computed<KitchenTicket[]>(() => {
    const groups = new Map<number, KitchenItem[]>();

    for (const item of this.items()) {
      // SERVED work leaves the active board as soon as the server confirms it.
      if (item.itemStatus === 'SERVED' || item.itemStatus === 'CANCELLED') continue;

      const lines = groups.get(item.orderId) ?? [];
      lines.push(item);
      groups.set(item.orderId, lines);
    }

    return Array.from(groups.entries())
      .map(([orderId, lines]) => ({
        orderId,
        invoiceNumber: lines[0].invoiceNumber,
        tableNumber: lines[0].tableNumber,
        orderType: lines[0].orderType,
        source: lines[0].source,
        customerNote: lines[0].customerNote,
        orderCreatedAt: lines[0].orderCreatedAt,
        orderStatus: lines[0].orderStatus,
        items: lines,
      }))
      .sort((a, b) =>
        a.orderCreatedAt.localeCompare(b.orderCreatedAt) || a.orderId - b.orderId,
      );
  });

  readonly pendingCount = computed(() =>
    this.countByStatus('PENDING'),
  );
  readonly preparingCount = computed(() =>
    this.countByStatus('PREPARING'),
  );
  readonly readyCount = computed(() => this.countByStatus('READY'));

  private readonly pollIntervalMs = 10_000;
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  /** Guards a slow branch-A response from replacing branch-B rows. */
  private loadToken = 0;

  /**
   * The board follows the app-wide branch context. A branch switch clears
   * the old tickets immediately and starts a fresh poll cycle, so a chef can
   * never act on rows that belong to the previous branch.
   */
  private readonly branchWatcher = effect(() => {
    const branchId = this.selectedBranchId();
    this.switchBranch(branchId);
  });

  ngOnInit(): void {
    this.branchContext.load();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.branchWatcher.destroy();
  }

  selectBranch(branchId: number | null): void {
    this.branchContext.select(branchId);
  }

  refresh(): void {
    this.load(this.selectedBranchId(), false);
  }

  advance(item: KitchenItem): void {
    const branchId = this.selectedBranchId();
    if (branchId == null || this.updatingIds().has(item.id)) return;

    const target = this.nextStatus(item.itemStatus);
    if (target == null) return;

    this.actionError.set(null);
    this.markUpdating(item.id, true);

    const request =
      target === 'PREPARING'
        ? this.kitchenService.markPreparing(branchId, item.id)
        : target === 'READY'
          ? this.kitchenService.markReady(branchId, item.id)
          : this.kitchenService.markServed(branchId, item.id);

    request
      .pipe(finalize(() => this.markUpdating(item.id, false)))
      .subscribe({
        next: (updated) => {
          this.items.update((items) =>
            items.map((current) =>
              current.id === updated.id ? updated : current,
            ),
          );
          // Reconcile with the server immediately; the next poll is only a
          // fallback for changes made by another kitchen device.
          this.load(branchId, false);
        },
        error: (error) => {
          this.actionError.set(
            extractErrorMessage(error, 'Failed to update the kitchen item.'),
          );
        },
      });
  }

  // ── display helpers ─────────────────────────────────────────────────

  readonly itemName = (item: KitchenItem): string =>
    item.itemNameSnapshot?.['en'] ||
    Object.values(item.itemNameSnapshot ?? {})[0] ||
    `Item #${item.id}`;

  readonly ticketTitle = (ticket: KitchenTicket): string =>
    ticket.invoiceNumber ?? `#${ticket.orderId}`;

  readonly tableLabel = (ticket: KitchenTicket): string =>
    ticket.tableNumber ??
    (ticket.orderType === 'TAKEAWAY' ? 'Takeaway' : 'No table');

  readonly sourceLabel = (source: string): string => ({
    QR_CUSTOMER: 'Customer',
    WAITER: 'Waiter',
    CASHIER: 'Cashier',
  })[source] ?? source;

  readonly statusLabel = (status: KitchenItemStatus): string => ({
    PENDING: 'New',
    PREPARING: 'Preparing',
    READY: 'Ready',
    SERVED: 'Served',
    CANCELLED: 'Cancelled',
  })[status] ?? status;

  readonly statusBadge = (status: KitchenItemStatus): string => ({
    PENDING: 'badge-warning',
    PREPARING: 'badge-info',
    READY: 'badge-success',
    SERVED: 'badge-neutral',
    CANCELLED: 'badge-error',
  })[status] ?? 'badge-ghost';

  readonly actionLabel = (status: KitchenItemStatus): string =>
    ({
      PENDING: 'Start cooking',
      PREPARING: 'Mark ready',
      READY: 'Mark served',
      SERVED: '',
      CANCELLED: '',
    })[status] ?? '';

  /** Compact age label, useful to a chef deciding what to cook first. */
  readonly elapsedLabel = (createdAt: string): string => {
    const started = Date.parse(createdAt);
    if (!Number.isFinite(started)) return '';

    const minutes = Math.max(
      0,
      Math.floor((Date.now() - started) / 60_000),
    );
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m`;

    const hours = Math.floor(minutes / 60);
    const remaining = minutes % 60;
    return remaining === 0 ? `${hours}h` : `${hours}h ${remaining}m`;
  };

  // ── loading and polling ─────────────────────────────────────────────

  private switchBranch(branchId: number | null): void {
    this.stopPolling();
    this.loadToken++;
    this.items.set([]);
    this.error.set(null);
    this.actionError.set(null);
    this.lastUpdated.set(null);

    if (branchId == null) {
      this.loading.set(false);
      this.refreshing.set(false);
      return;
    }

    this.loading.set(true);
    this.load(branchId, true);
    this.pollHandle = setInterval(
      () => this.load(branchId, false),
      this.pollIntervalMs,
    );
  }

  private load(branchId: number | null, initial: boolean): void {
    if (branchId == null) return;

    const token = ++this.loadToken;
    if (initial) this.loading.set(true);
    this.refreshing.set(true);
    this.error.set(null);

    this.kitchenService.list(branchId).subscribe({
      next: (items) => {
        if (token !== this.loadToken || branchId !== this.selectedBranchId()) {
          return;
        }

        this.items.set(items);
        this.lastUpdated.set(new Date());
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: (error) => {
        if (token !== this.loadToken || branchId !== this.selectedBranchId()) {
          return;
        }

        // A failed poll keeps the last good board on screen. The chef can
        // still see the tickets they already had while the connection returns.
        this.error.set(
          extractErrorMessage(error, 'Failed to load the kitchen display.'),
        );
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  private stopPolling(): void {
    if (this.pollHandle != null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private markUpdating(itemId: number, updating: boolean): void {
    this.updatingIds.update((current) => {
      const next = new Set(current);
      if (updating) next.add(itemId);
      else next.delete(itemId);
      return next;
    });
  }

  private countByStatus(status: KitchenItemStatus): number {
    return this.items().filter((item) => item.itemStatus === status).length;
  }

  private nextStatus(
    status: KitchenItemStatus,
  ): 'PREPARING' | 'READY' | 'SERVED' | null {
    if (status === 'PENDING') return 'PREPARING';
    if (status === 'PREPARING') return 'READY';
    if (status === 'READY') return 'SERVED';
    return null;
  }
}
