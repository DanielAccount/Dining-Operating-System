export interface MenuItem {
    id: number;
    category: CategorySummary;
    localizedNames: { en?: string; mm?: string };
    localizedDescriptions: { en?: string; mm?: string };
    basePrice: number;
    imageUrl: string | null;
    displayOrder: number;
    /** false = retired: hidden from every customer menu. */
    isActive: boolean;
    modifierGroups: MenuItemModifierGroup[];
    createdAt: string;
    updatedAt: string;
}

export interface CategorySummary {
    id: number;
    localizedNames: { en?: string; mm?: string };
    displayOrder: number;
}

export interface MenuItemModifierGroup {
    id: number;
    modifierGroupId: number;
    modifierGroupLocalizedNames: { en?: string; mm?: string };
    displayOrder: number;
    requiredOverride: boolean | null;
    isActive: boolean;
}

export interface MenuItemCreateRequest {
    categoryId: number;
    localizedNames: { en?: string; mm?: string };
    localizedDescriptions: { en?: string; mm?: string };
    basePrice: number;
    imageUrl?: string;
    /** Position within the category. Omit to append at the end. */
    displayOrder?: number;
    isActive?: boolean;
    modifierGroups?: MenuItemModifierGroupRequest[];
}

export interface MenuItemUpdateRequest {
    categoryId: number;
    localizedNames: { en?: string; mm?: string };
    localizedDescriptions: { en?: string; mm?: string };
    basePrice: number;
    imageUrl?: string;
    /** New position within the category. Omit to keep the current position. */
    displayOrder?: number;
    isActive?: boolean;
    modifierGroups?: MenuItemModifierGroupRequest[];
}

export interface MenuItemModifierGroupRequest {
    modifierGroupId: number;
    displayOrder?: number;
    requiredOverride?: boolean;
    isActive?: boolean;
}

/**
 * One row of the branch pricing grid.
 *
 * The three availability levels each answer a different question, and
 * `isOrderable` is the single resolved answer. The UI must not re-derive it.
 *
 *  - itemIsActive  tenant-wide   is the dish on the menu at all?
 *  - isAvailable   per branch    does THIS branch offer it?
 *  - isInStock     per branch    does THIS branch have it today?
 */
export interface BranchMenuItemRow {
    branchMenuItemId: number | null;   // null = no branch row yet (global defaults)
    menuItemId: number;
    localizedNames: { en?: string; mm?: string };
    category: CategorySummary;
    basePrice: number;
    priceOverride: number | null;      // null = follow base price
    effectivePrice: number;
    itemIsActive: boolean;
    isAvailable: boolean;
    isInStock: boolean;
    isOrderable: boolean;
}

export interface BranchMenuItemUpsertRequest {
    priceOverride: number | null;
    isAvailable: boolean;
    isInStock?: boolean;               // omit to keep the current value
}

export interface BranchMenuItemBulkUpsertRequest {
    items: {
        menuItemId: number;
        priceOverride: number | null;
        isAvailable: boolean;
        isInStock?: boolean;
    }[];
}
