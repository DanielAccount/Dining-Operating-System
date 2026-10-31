import { StorefrontItem, StorefrontModifierOption } from './storefront_model';

/**
 * One item-configuration in the cart. Two lattes with different modifier
 * selections are TWO lines - the kitchen reads them differently - so the
 * line identity is item + sorted option ids, never just the item.
 */
export interface CartLine {
    key: string;
    item: StorefrontItem;
    quantity: number;
    selectedOptions: SelectedOption[];
}

export interface SelectedOption {
    groupId: number;
    id: number;
    name: string;
    price: number;
}

/** Stable identity for a line: item + sorted option ids. */
export function lineKey(itemId: number, optionIds: number[]): string {
    return `${itemId}:${optionIds.slice().sort((a, b) => a - b).join('-')}`;
}

/** Unit price as the cart shows it: menu price + selected option extras. */
export function lineUnitPrice(line: CartLine): number {
    return line.item.price + line.selectedOptions.reduce((sum, o) => sum + o.price, 0);
}

export function lineTotal(line: CartLine): number {
    return lineUnitPrice(line) * line.quantity;
}

export function toSelectedOptions(item: StorefrontItem, optionIds: number[]): SelectedOption[] {
    // Options do not carry their group id on the wire (the backend nests
    // them under groups), so the group is captured while walking the tree.
    const byId = new Map<number, { groupId: number; option: StorefrontModifierOption }>();
    for (const group of item.modifierGroups) {
        for (const option of group.options) {
            byId.set(option.id, { groupId: group.id, option });
        }
    }
    return optionIds
        .map(id => byId.get(id))
        .filter((entry): entry is { groupId: number; option: StorefrontModifierOption } => !!entry)
        .map(({ groupId, option }) => ({
            groupId,
            id: option.id,
            name: optionDisplayName(option),
            price: option.extraPrice,
        }));
}

/**
 * Can this group's selection be added to the order? minSelect/maxSelect are
 * the source of truth; `required` only forces minSelect >= 1 at group
 * creation time (the backend rejects weaker groups), so it does not need a
 * separate rule here.
 */
export function groupSelectionValid(minSelect: number, maxSelect: number, selected: number): boolean {
    if (selected < minSelect) {
        return false;
    }
    // maxSelect 0 means "no cap" in the menu domain.
    return maxSelect === 0 || selected <= maxSelect;
}

/** English first, then whatever exists - the same rule the storefront renders. */
export function optionDisplayName(option: StorefrontModifierOption): string {
    const names = option.localizedNames ?? {};
    return names['en'] ?? Object.values(names)[0] ?? '';
}

export function itemDisplayName(item: StorefrontItem): string {
    const names = item.localizedNames ?? {};
    return names['en'] ?? Object.values(names)[0] ?? '';
}
