import { Injectable, computed, signal } from '@angular/core';
import { StorefrontItem } from './storefront_model';
import { CartLine, SelectedOption, lineKey, lineTotal } from './cart.models';

/**
 * The cart for ONE table visit.
 *
 * Deliberately COMPONENT-PROVIDD (providers: [CartService] on the
 * storefront), not a root singleton: a root instance would carry table
 * A's cart into table B when the same phone scans a second sticker - and
 * a fresh component instance per /t/:slug navigation is exactly the
 * lifecycle a cart should have.
 */
@Injectable()
export class CartService {

    private readonly linesState = signal<CartLine[]>([]);

    readonly lines = this.linesState.asReadonly();

    readonly itemCount = computed(() =>
        this.linesState().reduce((sum, line) => sum + line.quantity, 0));

    readonly total = computed(() =>
        this.linesState().reduce((sum, line) => sum + lineTotal(line), 0));

    /**
     * Adds an item with its selected options. Same item + same options
     * merges into one line with quantity +1; different options are a
     * different line, because the kitchen reads them differently.
     */
    add(item: StorefrontItem, selectedOptions: SelectedOption[], quantity = 1): void {
        const key = lineKey(
            item.id,
            selectedOptions.map(o => o.id));

        this.linesState.update(lines => {
            const existing = lines.find(l => l.key === key);
            if (existing) {
                return lines.map(l =>
                    l.key === key ? { ...l, quantity: l.quantity + quantity } : l);
            }
            return [...lines, { key, item, quantity, selectedOptions: [...selectedOptions] }];
        });
    }

    increment(key: string): void {
        this.linesState.update(lines =>
            lines.map(l => l.key === key ? { ...l, quantity: l.quantity + 1 } : l));
    }

    /** Quantity can never fall below 1 - removing is a separate action. */
    decrement(key: string): void {
        this.linesState.update(lines =>
            lines.map(l =>
                l.key === key ? { ...l, quantity: Math.max(1, l.quantity - 1) } : l));
    }

    remove(key: string): void {
        this.linesState.update(lines => lines.filter(l => l.key !== key));
    }

    clear(): void {
        this.linesState.set([]);
    }
}
