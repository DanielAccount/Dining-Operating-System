import { Component, OnInit, computed, inject, signal, ViewChild } from "@angular/core";
import { DatePipe } from "@angular/common";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute } from "@angular/router";
import { HttpErrorResponse } from "@angular/common/http";
import { StorefrontService, OrderSummary } from "./storefront_service";
import { PublicTableMenuResponse, StorefrontItem } from "./storefront_model";
import { ImageUrlPipe } from "../../shared/pipes/image_url.pipe";
import { extractErrorMessage } from "../../shared/http/error-message";
import { CartService } from "./cart.service";
import { ItemOptionsSheetComponent } from "./item-options-sheet";
import { CartSheetComponent } from "./cart-sheet";

/**
 * Route: /t/:slug — the PUBLIC customer storefront (Phase 3), grown into
 * the ordering surface (Phase 4).
 *
 * <p>Cart lifecycle: the CartService is provided HERE, not in root - a
 * component instance per scan means a fresh cart per table, and a cart
 * from table A can never ride along to table B.</p>
 *
 * <p>Idempotency: every submission attempt carries a client-generated
 * UUID. The key is RETAINED across retries of the same attempt (a flaky
 * signal must replay, never duplicate) and released on any definitive
 * outcome (placed, rejected) so the next submission is a genuinely new
 * attempt.</p>
 *
 * <p>Session re-attach: the first successful submission stores the table's
 * sessionCode under a slug-scoped localStorage key; on the next scan of
 * the same table, "Your orders" shows the table's orders again.</p>
 */
@Component({
    selector: 'app-storefront',
    standalone: true,
    imports: [ImageUrlPipe, DatePipe, ItemOptionsSheetComponent, CartSheetComponent],
    providers: [CartService],
    templateUrl: './storefront.html'
})
export class StorefrontComponent implements OnInit {

    private readonly route = inject(ActivatedRoute);
    private readonly storefrontService = inject(StorefrontService);
    private readonly title = inject(Title);

    /** Component-scoped: fresh cart per scan (see class javadoc). */
    readonly cart = inject(CartService);

    @ViewChild(ItemOptionsSheetComponent) private optionsSheet?: ItemOptionsSheetComponent;
    @ViewChild(CartSheetComponent) private cartSheet?: CartSheetComponent;

    // ── menu state (Phase 3) ────────────────────────────────────────────
    readonly menu = signal<PublicTableMenuResponse | null>(null);
    readonly loading = signal(true);
    readonly notFound = signal(false);
    readonly networkFailed = signal(false);
    readonly activeCategoryId = signal<number | null>(null);

    readonly activeCategory = computed(() => {
        const menu = this.menu();
        if (!menu) return null;
        return menu.categories.find(c => c.id === this.activeCategoryId()) ?? null;
    });

    // ── ordering state (Phase 4) ────────────────────────────────────────
    readonly orders = signal<OrderSummary[]>([]);
    readonly submitting = signal(false);
    readonly submitError = signal<string | null>(null);

    /** The idempotency key of the attempt in flight/retrying - see javadoc. */
    private readonly pendingKey = signal<string | null>(null);

    private slug: string | null = null;

    ngOnInit(): void {
        this.slug = this.route.snapshot.paramMap.get('slug');
        this.loadMenu(this.slug);
    }

    retry(): void {
        this.loadMenu(this.slug);
    }

    // ── menu loading ────────────────────────────────────────────────────

    private loadMenu(slug: string | null): void {
        if (!slug) {
            this.loading.set(false);
            this.notFound.set(true);
            return;
        }

        this.loading.set(true);
        this.notFound.set(false);
        this.networkFailed.set(false);

        this.storefrontService.menu(slug).subscribe({
            next: menu => {
                this.menu.set(menu);
                this.activeCategoryId.set(menu.categories.length > 0 ? menu.categories[0].id : null);
                this.loading.set(false);
                this.title.setTitle(`${menu.businessName} · Table ${menu.tableNumber}`);
                this.loadOrders(slug);
            },
            error: (err: HttpErrorResponse) => {
                this.menu.set(null);
                this.loading.set(false);
                if (err.status === 0) {
                    this.networkFailed.set(true);
                } else {
                    this.notFound.set(true);
                }
            }
        });
    }

    // ── orders for a returning phone ──────────────────────────────────

    /** localStorage key is slug-scoped: one session code per table. */
    private sessionKey(slug: string): string {
        return `dos.session.${slug}`;
    }

    private storedSessionCode(slug: string): string | null {
        try {
            return localStorage.getItem(this.sessionKey(slug));
        } catch {
            return null; // private mode / storage disabled - orders stay hidden
        }
    }

    private loadOrders(slug: string): void {
        const sessionCode = this.storedSessionCode(slug);
        if (!sessionCode) {
            return;
        }

        this.storefrontService.orders(slug, sessionCode).subscribe({
            next: orders => this.orders.set(orders),
            // A failed refresh must never break the menu: the orders list
            // simply stays whatever it was.
            error: () => this.orders.set([]),
        });
    }

    // ── cart interactions ─────────────────────────────────────────────

    addToCart(item: StorefrontItem): void {
        if (item.soldOut) {
            return;
        }

        if (item.modifierGroups.length === 0) {
            this.cart.add(item, []);
        } else {
            this.optionsSheet?.open(item);
        }
    }

    openCart(): void {
        this.cartSheet?.open();
    }

    // ── submission ─────────────────────────────────────────────────────

    submitCart(note: string): void {
        const slug = this.slug;
        const lines = this.cart.lines();
        if (!slug || lines.length === 0 || this.submitting()) {
            return;
        }

        // First attempt of this cart gets a fresh key; a retry after a
        // dropped connection reuses it so the phone can never double-order.
        if (!this.pendingKey()) {
            this.pendingKey.set(this.newIdempotencyKey());
        }

        this.submitting.set(true);
        this.submitError.set(null);

        this.storefrontService.submitOrder(slug, {
            idempotencyKey: this.pendingKey()!,
            customerNote: note || undefined,
            items: lines.map(line => ({
                menuItemId: line.item.id,
                quantity: line.quantity,
                modifiers: line.selectedOptions.length > 0
                    ? line.selectedOptions.map(o => ({ modifierOptionId: o.id }))
                    : undefined,
            })),
        }).subscribe({
            next: order => {
                // Definitive success: release the key, remember the table's
                // session, start a fresh cart.
                this.pendingKey.set(null);
                this.submitting.set(false);
                this.cart.clear();
                this.cartSheet?.close();
                try {
                    if (order.sessionCode) {
                        localStorage.setItem(this.sessionKey(slug), order.sessionCode);
                    }
                } catch { /* storage disabled - orders just won't re-attach */ }
                this.loadOrders(slug);
            },
            error: (err: HttpErrorResponse) => {
                this.submitting.set(false);

                if (err.status === 0) {
                    // The request MAY have landed - keeping the key makes the
                    // customer's next tap a replay instead of a duplicate.
                    this.submitError.set(
                        extractErrorMessage(err, 'The order may not have gone through. Try again.') +
                        ' If it already went through, trying again will not double it.');
                } else {
                    // 409 (sold out / changed) or 404 (dead code): definitively
                    // NOT placed, so the key is unconsumed - next attempt is
                    // genuinely new.
                    this.pendingKey.set(null);
                    this.submitError.set(extractErrorMessage(err, 'The order could not be placed.'));
                }
            }
        });
    }

    cartDismissed(): void {
        this.submitError.set(null);
    }

    private newIdempotencyKey(): string {
        return crypto.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    }

    // ── template helpers ────────────────────────────────────────────────

    nameOf(map: Record<string, string> | null | undefined): string {
        if (!map) return '';
        return map['en'] ?? Object.values(map)[0] ?? '';
    }

    /** daisyUI badge class per order status - the customer's live pulse. */
    statusBadge(status: string): string {
        switch (status) {
            case 'PENDING':    return 'badge-warning';
            case 'ACCEPTED':
            case 'PREPARING': return 'badge-info';
            case 'READY':      return 'badge-success';
            case 'COMPLETED':  return 'badge-neutral';
            case 'CANCELLED':  return 'badge-error';
            default:           return 'badge-ghost';
        }
    }

    /** What the status means to a diner, not to the kitchen. */
    statusLabel(status: string): string {
        switch (status) {
            case 'PENDING':   return 'Sent';
            case 'ACCEPTED':  return 'Accepted';
            case 'PREPARING': return 'Preparing';
            case 'READY':     return 'Ready';
            case 'COMPLETED': return 'Served';
            case 'CANCELLED': return 'Cancelled';
            default:          return status;
        }
    }
}
