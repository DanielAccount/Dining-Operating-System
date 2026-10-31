import { Component, ElementRef, inject, signal, ViewChild } from "@angular/core";
import { CartService } from "./cart.service";
import { groupSelectionValid, optionDisplayName, toSelectedOptions } from "./cart.models";
import { StorefrontItem } from "./storefront_model";

/**
 * "Choose options" sheet: configures ONE item before it joins the cart.
 * Each modifier group enforces its own min/max; "Add" unlocks only when
 * every group is valid. Sold-out items never reach this sheet.
 */
@Component({
    selector: 'app-item-options-sheet',
    standalone: true,
    imports: [],
    templateUrl: './item-options-sheet.html',
})
export class ItemOptionsSheetComponent {

    private readonly cart = inject(CartService);

    @ViewChild('dialog') private dialog?: ElementRef<HTMLDialogElement>;

    /** The item being configured; null means the sheet is closed. */
    protected readonly item = signal<StorefrontItem | null>(null);

    /** groupId -> selected option ids. */
    protected readonly selection = signal<Map<number, number[]>>(new Map());

    readonly valid = signal(false);

    open(item: StorefrontItem): void {
        this.item.set(item);
        this.selection.set(new Map());
        this.valid.set(item.modifierGroups.every(g => groupSelectionValid(g.minSelect, g.maxSelect, 0)));
        try {
            this.dialog?.nativeElement.showModal();
        } catch { /* unsupported JS environment - open() still resets state */ }

    }

    // ── selection handling ─────────────────────────────────────────────

    protected isSelected(groupId: number, optionId: number): boolean {
        return this.selection().get(groupId)?.includes(optionId) ?? false;
    }

    protected toggle(groupId: number, optionId: number, maxSelect: number): void {
        this.selection.update(map => {
            const next = new Map(map);
            const current = next.get(groupId) ?? [];

            if (current.includes(optionId)) {
                next.set(groupId, current.filter(id => id !== optionId));
            } else if (maxSelect === 0 || current.length < maxSelect) {
                // maxSelect 0 = "no cap" in the menu domain; otherwise an
                // extra pick beyond the cap is simply ignored - the
                // friendliest behavior for a thumb.
                next.set(groupId, [...current, optionId]);
            }

            return next;
        });
        this.revalidate();
    }

    private revalidate(): void {
        const item = this.item();
        if (!item) {
            this.valid.set(false);
            return;
        }
        this.valid.set(item.modifierGroups.every(group => {
            const selected = this.selection().get(group.id)?.length ?? 0;
            return groupSelectionValid(group.minSelect, group.maxSelect, selected);
        }));
    }

    // ── adding ─────────────────────────────────────────────────────────

    protected readonly optionName = optionDisplayName;

    /** English first, then whatever exists - same rule as the storefront. */
    protected nameOf(names: Record<string, string> | null | undefined): string {
        if (!names) return '';
        return names['en'] ?? Object.values(names)[0] ?? '';
    }

    protected add(): void {
        const item = this.item();
        if (!item || !this.valid()) {
            return;
        }

        const optionIds = [...this.selection().values()].flat();
        this.cart.add(item, toSelectedOptions(item, optionIds));
        this.close();
    }

    protected close(): void {
        try {
            this.dialog?.nativeElement.close();
        } catch { /* nothing else depends on the dialog's visual state */ }
    }
}
