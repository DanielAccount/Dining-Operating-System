export interface Category {
    id: number;
    localizedNames: {
        en?: string;
        mm?: string;
    };
    displayOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface CategoryCreateRequest {
    localizedNames: {
        en?: string;
        mm?: string;
    };
    displayOrder: number;
}