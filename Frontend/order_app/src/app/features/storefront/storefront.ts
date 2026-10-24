import { Component, OnInit, computed, inject, signal } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute } from "@angular/router";
import { HttpErrorResponse } from "@angular/common/http";
import { StorefrontService } from "./storefront_service";
import { PublicTableMenuResponse } from "./storefront_model";
import { ImageUrlPipe } from "../../shared/pipes/image_url.pipe";

/**
 * Route: /t/:slug — the PUBLIC customer storefront (Phase 3, Milestone 5).
 *
 * This is what a phone shows after scanning the sticker on a table: no
 * login, no sidebar, no roles. Everything on screen comes from one public
 * endpoint call, availability already decided server-side — this component
 * renders soldOut badges, it never re-derives availability rules.
 *
 * A dead or closed-table code lands on the not-found state, and the copy
 * deliberately never explains WHICH business, branch or table the code
 * belonged to (the backend's 404 is generic for exactly the same reason).
 *
 * Phase 4 will grow this same screen into ordering: "add to order" buttons,
 * the cart, and order submission against this table's slug.
 */
@Component({
    selector: 'app-storefront',
    standalone: true,
    imports: [ImageUrlPipe],
    templateUrl: './storefront.html'
})
export class StorefrontComponent implements OnInit {

    private readonly route = inject(ActivatedRoute);
    private readonly storefrontService = inject(StorefrontService);
    private readonly title = inject(Title);

    readonly menu = signal<PublicTableMenuResponse | null>(null);
    readonly loading = signal(true);

    /** True when the slug is dead, the branch is closed, or the tenant stopped serving. */
    readonly notFound = signal(false);
    /** True when the request itself failed (offline, server down) - retryable. */
    readonly networkFailed = signal(false);

    readonly activeCategoryId = signal<number | null>(null);

    /** The category whose items are currently on screen. */
    readonly activeCategory = computed(() => {
        const menu = this.menu();
        if (!menu) return null;
        return menu.categories.find(c => c.id === this.activeCategoryId()) ?? null;
    });

    ngOnInit(): void {
        this.loadMenu(this.route.snapshot.paramMap.get('slug'));
    }

    retry(): void {
        this.loadMenu(this.route.snapshot.paramMap.get('slug'));
    }

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
            },
            error: (err: HttpErrorResponse) => {
                this.menu.set(null);
                this.loading.set(false);
                if (err.status === 0) {
                    this.networkFailed.set(true);
                } else {
                    // 404 by design hides why - closed branch, suspended
                    // tenant and unknown slug are all "Menu not found".
                    this.notFound.set(true);
                }
            }
        });
    }

    /** English when present, else the first localized value (seeded data is en + mm). */
    nameOf(map: Record<string, string> | null | undefined): string {
        if (!map) return '';
        return map['en'] ?? Object.values(map)[0] ?? '';
    }
}
