export interface ModifierOption {
    id: number,
    localizedNames: { en?: string; mm?: string };
    extraPrice: number;
    isAvailable: boolean;
}

export interface ModifierGroup {
    id: number;
    localizedNames: { en?: string; mm?: string };
    isRequired: boolean;
    minSelect: number;
    maxSelect: number;
    isActive: boolean;
    options: ModifierOption[];
    createdAt: string;
    updatedAt: string;
}

export interface ModifierOptionRequest {
    /**
     * The existing option's id. MUST be sent when editing: without it the
     * backend treats the row as new and deletes the old one, so saving a group
     * would give every option a new id.
     * Omit it only for a genuinely new option.
     */
    id?: number | null;
    localizedNames: { en?: string; mm?: string };
    extraPrice: number;
    isAvailable: boolean;
}

export interface ModifierGroupCreateRequest {
    localizedNames: { en?: string; mm?: string };
    isRequired: boolean;
    minSelect: number;
    maxSelect: number;
    options: ModifierOptionRequest[];
    /** null/omitted leaves an existing group untouched. */
    isActive?: boolean;
}