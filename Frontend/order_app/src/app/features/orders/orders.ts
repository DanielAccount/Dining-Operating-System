import { ChangeDetectorRef, Component, ElementRef, inject, OnInit, signal, ViewChild } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { BranchContextService } from "../../core/branch/branch-context.service";
import { HasPermissionDirective } from "../../shared/directives/has_permission_directuve";
import { extractErrorMessage } from "../../shared/http/error-message";
import { StaffOrderService } from "./order.service";
import { Order, OrderDetail, OrderStatus } from "./order.models";
import { TableResponse } from "../tables/tables_model";
import { TablesService } from "../tables/tables_service";
import { ProductService } from "../menu_management/products/product_service";
import { MenuItem } from "../menu_management/models/products_model";

/** One line in the staff create-order form (before it becomes a request). */
interface DraftLine {
    key: string;
    itemId: number;
    name: string;
    price: number;
    quantity: number;
    modifierOptions: { id: number; name: string; price: number }[];
}

/**
 * Route: /app/orders (permission: ORDER_VIEW)
 *
 * The waiter/cashier working screen - the page waiters land on after login.
 * Phase 4 scope: the branch's live order list, accept and cancel, and the
 * create-order-at-a-table form for guest orders taken by hand. The kitchen's
 * item-level transitions (preparing/ready/served) are Phase 5 and are
 * deliberately absent here.
 */
@Component({
    selector: 'app-orders',
    standalone: true,
    imports: [CommonModule, FormsModule, HasPermissionDirective],
    templateUrl: './orders.html',
})
export class OrdersComponent implements OnInit {

    private readonly orderService = inject(StaffOrderService);
    private readonly tablesService = inject(TablesService);
    private readonly productService = inject(ProductService);
    private readonly branchContext = inject(BranchContextService);
    private readonly cdr = inject(ChangeDetectorRef);

    // Branch state is app-wide (the sidebar picker writes it too); this page
    // only follows it, exactly like the branch pricing screen.
    readonly branches = this.branchContext.branches;
    readonly selectedBranchId = this.branchContext.selectedBranchId;
    readonly isTenantWide = this.branchContext.isTenantWide;

    // ── data ────────────────────────────────────────────────────────────
    readonly orders = signal<Order[]>([]);
    readonly tables = signal<TableResponse[]>([]);
    readonly draftLines = signal<DraftLine[]>([]);
    /** The tenant-wide menu; the create form picks items from it. */
    readonly menuItems = signal<MenuItem[]>([]);
    readonly menuLoading = signal(false);

    // ── ui state ────────────────────────────────────────────────────────
    readonly loading = signal(false);
    readonly saving = signal(false);
    readonly error = signal<string | null>(null);
    readonly successMessage = signal<string | null>(null);
    readonly listError = signal<string | null>(null);
    readonly createError = signal<string | null>(null);

    /** filters ─────────────────────────────────────────────────────────── */
    readonly statusFilter = signal<'ACTIVE' | 'ALL' | OrderStatus>('ACTIVE');

    readonly filteredOrders = () => {
        const orders = this.orders();
        if (this.statusFilter() === 'ALL') {
            return orders;
        }
        if (this.statusFilter() === 'ACTIVE') {
            const live = ['PENDING', 'ACCEPTED', 'PREPARING', 'READY'];
            return orders.filter(o => live.includes(o.status));
        }
        return orders.filter(o => o.status === this.statusFilter());
    };

    // ── create-order form state ─────────────────────────────────────────
    @ViewChild('createDialog') createDialog!: ElementRef<HTMLDialogElement>;
    createTableId: number | null = null;
    createNote = '';

    // ── detail panel state ──────────────────────────────────────────────
    @ViewChild('detailDialog') detailDialog!: ElementRef<HTMLDialogElement>;
    detail = signal<OrderDetail | null>(null);

    /** Guards a slow branch-A response from overwriting branch-B rows. */
    private loadToken = 0;

    ngOnInit(): void {
        this.loadOrders(this.selectedBranchId());
        this.loadTables(this.selectedBranchId());
        this.loadMenu();
    }

    selectBranch(branchId: number | null): void {
        this.branchContext.select(branchId);
    }

    // ── loading ─────────────────────────────────────────────────────────

    private loadMenu(): void {
        this.menuLoading.set(true);

        this.productService.getAll().subscribe({
            next: items => {
                // Only live items: a retired dish never reappears on a new
                // staff order, even if it is still on an old one.
                this.menuItems.set(items.filter(i => i.isActive));
                this.menuLoading.set(false);
            },
            error: () => {
                this.menuItems.set([]);
                this.menuLoading.set(false);
            }
        });
    }

    loadOrders(branchId: number | null): void {
        if (branchId == null) {
            this.orders.set([]);
            return;
        }

        this.loading.set(true);
        this.listError.set(null);

        this.orderService.list(branchId).subscribe({
            next: orders => {
                this.orders.set(orders);
                this.loading.set(false);
                this.cdr.detectChanges();
            },
            error: err => {
                this.loading.set(false);
                this.orders.set([]);
                this.listError.set(extractErrorMessage(err, 'Failed to load orders.'));
            }
        });
    }

    private loadTables(branchId: number | null): void {
        if (branchId == null) {
            this.tables.set([]);
            return;
        }

        this.tablesService.list(branchId).subscribe({
            next: tables => this.tables.set(tables),
            error: () => this.tables.set([]),
        });
    }

    // ── create order at a table ─────────────────────────────────────────

    openCreate(): void {
        this.createError.set(null);
        this.createNote = '';
        this.createTableId = this.tables()[0]?.id ?? null;
        this.draftLines.set([]);
        this.createDialog.nativeElement.showModal();
        if (this.createTableId == null) {
            // The picker is empty only when the branch has no tables yet.
            this.createError.set('This branch has no tables. Add one in Tables first.');
        }
    }

    /** Reuses the tenant-wide menu endpoint - no branch scoped variant needed. */
    addDraftItem(item: MenuItem): void {
        // Key includes chosen modifier ids so two configurations of the same
        // dish are two lines (same rule as the customer's cart).
        const key = `item-${item.id}`;

        this.draftLines.update(lines => {
            const existing = lines.find(l => l.key === key);
            if (existing) {
                return lines.map(l =>
                    l.key === key ? { ...l, quantity: l.quantity + 1 } : l);
            }
            return [...lines, {
                key,
                itemId: item.id,
                name: this.itemName(item),
                price: this.basePrice(item),
                quantity: 1,
                modifierOptions: [],
            }];
        });
    }

    incrementDraft(key: string): void {
        this.draftLines.update(lines =>
            lines.map(l => l.key === key ? { ...l, quantity: l.quantity + 1 } : l));
    }

    decrementDraft(key: string): void {
        this.draftLines.update(lines =>
            lines.map(l => l.key === key ? { ...l, quantity: Math.max(1, l.quantity - 1) } : l));
    }

    removeDraft(key: string): void {
        this.draftLines.update(lines => lines.filter(l => l.key !== key));
    }

    readonly draftTotal = () => this.draftLines().reduce(
        (sum, l) => sum + l.price * l.quantity, 0);

    submitCreate(): void {
        const branchId = this.selectedBranchId();
        if (branchId == null || this.createTableId == null || this.saving()) {
            return;
        }
        const lines = this.draftLines();
        if (lines.length === 0) {
            this.createError.set('Add at least one item to the order.');
            return;
        }

        this.saving.set(true);
        this.createError.set(null);

        this.orderService.createAtTable(branchId, this.createTableId, {
            // Staff create is a single deliberate click, but a dropped signal
            // must still not double-order - so the same UUID-per-attempt rule
            // applies here as on the customer's phone.
            idempotencyKey: this.newKey(),
            items: lines.map(l => ({
                menuItemId: l.itemId,
                quantity: l.quantity,
                modifiers: l.modifierOptions.length > 0
                    ? l.modifierOptions.map(o => ({ modifierOptionId: o.id }))
                    : undefined,
            })),
            customerNote: this.createNote.trim() || undefined,
        }).subscribe({
            next: order => {
                this.saving.set(false);
                this.createDialog.nativeElement.close();
                this.successMessage.set(`Order for table ${order.tableNumber ?? ''} placed - status: ${this.statusLabel(order.status)}.`);
                this.loadOrders(branchId);
            },
            error: err => {
                this.saving.set(false);
                this.createError.set(extractErrorMessage(err, 'Failed to place the order.'));
            }
        });
    }

    // ── transitions ─────────────────────────────────────────────────────

    accept(order: Order): void {
        const branchId = this.selectedBranchId();
        if (branchId == null || order.status !== 'PENDING') {
            return;
        }

        this.saving.set(true);
        this.error.set(null);

        this.orderService.accept(branchId, order.id).subscribe({
            next: () => {
                this.saving.set(false);
                this.successMessage.set(
                    `Order ${order.invoiceNumber ?? '#' + order.id} accepted - the kitchen can now see it.`);
                this.loadOrders(branchId);
            },
            error: err => {
                this.saving.set(false);
                this.error.set(extractErrorMessage(err, 'Failed to accept the order.'));
            }
        });
    }

    askCancel(order: Order): void {
        // Kept explicit: a cancel is a permanent record on the order, and
        // this screen's only destructive action.
        const reason = window.prompt(`Why is order ${order.invoiceNumber ?? '#' + order.id} being cancelled?`,
            "Guest changed mind");
        if (reason === null) {
            return;
        }

        const branchId = this.selectedBranchId();
        if (branchId == null) {
            return;
        }

        this.saving.set(true);
        this.error.set(null);

        this.orderService.cancel(branchId, order.id, reason).subscribe({
            next: () => {
                this.saving.set(false);
                this.successMessage.set(`Order cancelled. The customer's phone updates to Cancelled.`);
                this.loadOrders(branchId);
            },
            error: err => {
                this.saving.set(false);
                this.error.set(extractErrorMessage(err, 'Failed to cancel the order.'));
            }
        });
    }

    // ── detail ──────────────────────────────────────────────────────────

    openDetail(order: Order): void {
        const branchId = this.selectedBranchId();
        if (branchId == null) {
            return;
        }

        this.orderService.detail(branchId, order.id).subscribe({
            next: detail => {
                this.detail.set(detail);
                this.detailDialog.nativeElement.showModal();
            },
            error: err => this.error.set(extractErrorMessage(err, 'Failed to load the order.')),
        });
    }

    // ── template helpers ────────────────────────────────────────────────

    /** Display label for an order: its invoice number once minted, else its id. */
    readonly orderTitle = (order: Order): string => order.invoiceNumber ?? `#${order.id}`;

    readonly itemName = (item: MenuItem) =>
        item.localizedNames?.en || Object.values(item.localizedNames ?? {})[0] || `#${item.id}`;

    readonly basePrice = (item: MenuItem) => Number(item.basePrice);

    readonly statusBadge = (status: OrderStatus): string => ({
        PENDING: 'badge-warning',
        ACCEPTED: 'badge-info',
        PREPARING: 'badge-info',
        READY: 'badge-success',
        COMPLETED: 'badge-neutral',
        CANCELLED: 'badge-error',
    })[status] ?? 'badge-ghost';

    readonly statusLabel = (status: OrderStatus): string => ({
        PENDING: 'Sent',
        ACCEPTED: 'Accepted',
        PREPARING: 'Preparing',
        READY: 'Ready',
        COMPLETED: 'Served',
        CANCELLED: 'Cancelled',
    })[status] ?? status;

    readonly sourceLabel = (source: string): string => ({
        QR_CUSTOMER: 'Customer',
        WAITER: 'Waiter',
        CASHIER: 'Cashier',
    })[source] ?? source;

    private newKey(): string {
        return crypto.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    }
}
