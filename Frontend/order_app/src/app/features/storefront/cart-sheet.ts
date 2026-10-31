import { Component, ElementRef, inject, input, output, ViewChild } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { CartService } from "./cart.service";
import { itemDisplayName, lineTotal } from "./cart.models";

/**
 * The cart sheet: lines with steppers, a note for the kitchen, the total,
 * and the "Place order" button. Deliberately DUMB about HTTP - the parent
 * owns the submission (idempotency key, session code, error states), and
 * this component only renders lines and emits intent.
 */
@Component({
    selector: 'app-cart-sheet',
    standalone: true,
    imports: [FormsModule],
    templateUrl: './cart-sheet.html',
})
export class CartSheetComponent {

    /** The same component-scoped instance the storefront provided. */
    protected readonly cart = inject(CartService);

    @ViewChild('dialog') private dialog?: ElementRef<HTMLDialogElement>;

    // ── render-only state from the parent ──────────────────────────────
    readonly submitting = input(false);
    readonly submitError = input<string | null>(null);

    // ── intents, handled by the parent ─────────────────────────────────
    readonly placeOrder = output<string>();   // the customer note
    readonly dismissed = output<void>();

    protected note = '';

    // Small helpers the template binds to.
    protected readonly itemName = itemDisplayName;
    protected readonly lineTotal = lineTotal;

    open(): void {
        this.note = '';
        try {
            this.dialog?.nativeElement.showModal();
        } catch { /* unsupported JS environment - open() still resets state */ }
    }

    /** Public: the parent closes the sheet programmatically on success. */
    close(): void {
        this.dismissed.emit();
        // Defensive: a dialog that somehow fails to close must never abort the
        // caller's success path (order placed, cart cleared, session stored).
        try {
            this.dialog?.nativeElement.close();
        } catch { /* the visual state closed; nothing else depends on it */ }
    }

    protected submit(): void {
        this.placeOrder.emit(this.note.trim() || '');
    }
}
