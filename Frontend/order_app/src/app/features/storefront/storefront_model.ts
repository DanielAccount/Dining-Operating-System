/**
 * The public menu wire format, mirroring the backend's
 * PublicTableMenuResponse exactly. Every availability decision is already
 * made server-side: retired items and items this branch doesn't offer are
 * simply absent, and a branch that ran out arrives as soldOut=true.
 * This client renders those states - it must never re-derive them.
 */

export interface StorefrontModifierOption {
    id: number;
    localizedNames: Record<string, string>;
    extraPrice: number;
}

export interface StorefrontModifierGroup {
    id: number;
    localizedNames: Record<string, string>;
    required: boolean;
    minSelect: number;
    maxSelect: number;
    options: StorefrontModifierOption[];
}

export interface StorefrontItem {
    id: number;
    localizedNames: Record<string, string>;
    localizedDescriptions: Record<string, string> | null;
    imageUrl: string | null;
    /** Effective price for this branch - override, else base price. */
    price: number;
    /** Visible but not orderable: this branch has run out. */
    soldOut: boolean;
    modifierGroups: StorefrontModifierGroup[];
}

export interface StorefrontCategory {
    id: number;
    localizedNames: Record<string, string>;
    items: StorefrontItem[];
}

export interface PublicTableMenuResponse {
    businessName: string;
    address: string | null;
    branchName: string;
    tableNumber: string;
    categories: StorefrontCategory[];
}
