import {
    ChangeDetectorRef,
    Component,
    ElementRef,
    inject,
    OnDestroy,
    OnInit,
    signal,
    ViewChild
} from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { CommonModule } from "@angular/common";
import { finalize } from "rxjs";

import { CategoryService } from "../categories/category_service";
import { ModifierService } from "../modifiers/modifer_service";
import { ProductService } from "./product_service";
import { Category } from "../models/categories_model";
import { ModifierGroup } from "../models/modifiers_models";
import { MenuItem, MenuItemCreateRequest, MenuItemUpdateRequest } from "../models/products_model";
import { ImageUrlPipe } from "../../../shared/pipes/image_url.pipe";
import { extractErrorMessage } from "../../../shared/http/error-message";

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

@Component({
    selector: 'products_management',
    standalone: true,
    imports: [ReactiveFormsModule, CommonModule, ImageUrlPipe],
    templateUrl: './products.html'
})
export class ProductsComponent implements OnInit, OnDestroy {

    private readonly fb = inject(FormBuilder);
    private readonly categoryService = inject(CategoryService);
    private readonly modifierService = inject(ModifierService);
    private readonly productService = inject(ProductService);
    private readonly cdr = inject(ChangeDetectorRef);

    @ViewChild('productModal') productModal!: ElementRef<HTMLDialogElement>;
    @ViewChild('deleteConfirmModal') deleteConfirmModal!: ElementRef<HTMLDialogElement>;
    @ViewChild('imageInput') imageInput?: ElementRef<HTMLInputElement>;

    // ---- Data ----
    readonly categories = signal<Category[]>([]);
    readonly modifierGroups = signal<ModifierGroup[]>([]);
    readonly products = signal<MenuItem[]>([]);

    // ---- UI state ----
    editingProductId: number | null = null;
    /** The item being edited, kept so a save can preserve fields the form does
     *  not surface (per-item modifier group overrides, link order, link state). */
    editingProduct: MenuItem | null = null;
    deletingProduct: MenuItem | null = null;

    // ---- Image upload state ----
    /** The raw File the user picked, or null. Never sent to the API directly. */
    selectedFile: File | null = null;
    /** Preview shown in the modal: either a blob: URL (new pick) or the stored path. */
    readonly imagePreviewUrl = signal<string | null>(null);
    readonly uploadingImage = signal(false);
    readonly saving = signal(false);
    readonly imageError = signal<string | null>(null);
    readonly saveError = signal<string | null>(null);
    readonly listError = signal<string | null>(null);

    productForm = this.fb.group({
        categoryId: [null as number | null, Validators.required],
        nameEn: ['', Validators.required],
        nameMm: [''],
        price: [0, [Validators.required, Validators.min(0)]],
        imageUrl: [''],  /** Holds the stored server path (e.g. "/uploads/menu-items/abc.jpg"), not the file. */
        displayOrder: [1 as number | null, [Validators.min(1)]],
        descriptionEn: [''],
        descriptionMm: [''],
        /** Not shown as a field - the card toggle owns it. Kept in the form so
         *  an ordinary edit does not silently retire the item. */
        isActive: [true],
        selectedModifierGroupIds: this.fb.control<number[]>([]),
    });

    ngOnInit(): void {
        this.loadProducts();
        this.loadCategories();
        this.loadModifierGroups();
    }

    /** Release any outstanding blob URL so the browser can free the memory. */
    ngOnDestroy(): void {
        this.revokePreview();
    }

    // ---- Data loading ----

    loadProducts(): void {
        this.productService.getAll().subscribe({
            next: (data) => {
                this.products.set(data);
                this.listError.set(null);
                this.cdr.detectChanges();
            },
            error: (err) => {
                this.listError.set(this.extractError(err, 'Could not load menu items.'));
                console.error('Failed to load products', err);
            }
        });
    }

    loadCategories(): void {
        this.categoryService.getAll().subscribe({
            next: (data) => {
                this.categories.set(data);
                this.cdr.detectChanges();
            },
            error: (err) => console.error('Failed to load categories', err)
        });
    }

    loadModifierGroups(): void {
        this.modifierService.getAll().subscribe({
            next: (data) => {
                this.modifierGroups.set(data);
                this.cdr.detectChanges();
            },
            error: (err) => console.error('Failed to load modifier groups', err)
        });
    }

    // ---- Image handling ----

    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        this.imageError.set(null);

        const file = input.files?.[0];
        if (!file) {
            this.clearImageSelection();
            return;
        }

        // Client-side checks only. The backend re-validates type, size and
        // magic bytes, so this is a UX nicety, not a security boundary.
        if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
            this.imageError.set('Only JPEG, PNG, WebP or GIF images are allowed.');
            this.resetFileInput();
            return;
        }

        if (file.size > MAX_IMAGE_BYTES) {
            this.imageError.set('Image must be smaller than 5 MB.');
            this.resetFileInput();
            return;
        }

        this.selectedFile = file;

        // Show an instant local preview before anything is uploaded.
        this.revokePreview();
        this.imagePreviewUrl.set(URL.createObjectURL(file));
    }

    /** Discards the pending file and any preview, but keeps the stored imageUrl. */
    clearImageSelection(): void {
        this.selectedFile = null;
        this.revokePreview();
        this.resetFileInput();
    }

    /** Removes the image entirely — the next save clears it on the server. */
    removeImage(): void {
        this.clearImageSelection();
        this.productForm.patchValue({ imageUrl: '' });
    }

    private revokePreview(): void {
        const current = this.imagePreviewUrl();
        if (current?.startsWith('blob:')) {
            URL.revokeObjectURL(current);
        }
        this.imagePreviewUrl.set(null);
    }

    private resetFileInput(): void {
        if (this.imageInput) {
            this.imageInput.nativeElement.value = '';
        }
    }

    // ---- Modifier checkbox helpers ----

    isModifierSelected(id: number): boolean {
        return this.productForm.controls.selectedModifierGroupIds.value?.includes(id) ?? false;
    }

    toggleModifier(id: number, checked: boolean): void {
        const ctrl = this.productForm.controls.selectedModifierGroupIds;
        const current = ctrl.value ?? [];
        ctrl.setValue(checked ? [...current, id] : current.filter(v => v !== id));
    }

    // ---- Modal helpers ----

    openCreateModal(): void {
        this.editingProductId = null;
        this.editingProduct = null;
        this.clearImageSelection();
        this.imageError.set(null);
        this.saveError.set(null);
        this.productForm.reset({
            categoryId: null,
            nameEn: '',
            nameMm: '',
            price: 0,
            imageUrl: '',
            // Default to the end of the first category; the backend clamps
            // out-of-range values, so this never creates a hole.
            displayOrder: this.countInCategory(this.categories()[0]?.id ?? null) + 1,
            descriptionEn: '',
            descriptionMm: '',
            isActive: true,
            selectedModifierGroupIds: [],
        });
        this.productModal.nativeElement.showModal();
    }

    /** How many menu items currently sit in a category. */
    private countInCategory(categoryId: number | null): number {
        if (categoryId == null) return 0;
        return this.products().filter(p => p.category.id === categoryId).length;
    }

    openEditModal(product: MenuItem): void {
        this.editingProductId = product.id;
        this.editingProduct = product;
        this.clearImageSelection();
        this.imageError.set(null);
        this.saveError.set(null);

        const selectedIds = product.modifierGroups?.map(mg => mg.modifierGroupId) ?? [];

        this.productForm.patchValue({
            categoryId: product.category.id,
            nameEn: product.localizedNames?.en ?? '',
            nameMm: product.localizedNames?.mm ?? '',
            price: product.basePrice,
            imageUrl: product.imageUrl ?? '',
            displayOrder: product.displayOrder,
            descriptionEn: product.localizedDescriptions?.en ?? '',
            descriptionMm: product.localizedDescriptions?.mm ?? '',
            isActive: product.isActive,
            selectedModifierGroupIds: selectedIds,
        });

        // Show the already-stored image as the starting preview.
        this.imagePreviewUrl.set(product.imageUrl ?? null);

        this.productModal.nativeElement.showModal();
    }

    closeModal(): void {
        this.productModal.nativeElement.close();
        this.clearImageSelection();
        this.saveError.set(null);
        this.imageError.set(null);
        this.editingProductId = null;
        this.editingProduct = null;
    }

    // ---- Save ----

    saveProduct(): void {
        if (this.productForm.invalid) {
            this.productForm.markAllAsTouched();
            return;
        }

        this.saveError.set(null);
        const fv = this.productForm.getRawValue();

        const buildRequest = (imageUrl: string | undefined) => ({
            categoryId: fv.categoryId!,
            localizedNames: {
                en: fv.nameEn!.trim(),
                ...(fv.nameMm?.trim() ? { mm: fv.nameMm.trim() } : {}),
            },
            localizedDescriptions: {
                ...(fv.descriptionEn?.trim() ? { en: fv.descriptionEn.trim() } : {}),
                ...(fv.descriptionMm?.trim() ? { mm: fv.descriptionMm.trim() } : {}),
            },
            basePrice: fv.price!,
            imageUrl: imageUrl || undefined,
            // Null means "append at the end" on create and "leave it alone" on
            // update, so an ordinary edit never triggers a renumber.
            displayOrder: fv.displayOrder ?? undefined,
            isActive: fv.isActive ?? true,
            modifierGroups: (fv.selectedModifierGroupIds ?? []).map(id =>
                this.modifierGroupPayload(id)),
        });

        // A new file was picked: upload it first to obtain the stored path,
        // then create/update the menu item using that path.
        if (this.selectedFile) {
            this.uploadingImage.set(true);
            this.productService.uploadImageFile(this.selectedFile)
                .pipe(finalize(() => this.uploadingImage.set(false)))
                .subscribe({
                    next: (res) => this.submitSave(buildRequest(res.imageUrl)),
                    error: (err) => {
                        this.imageError.set(this.extractError(err, 'Image upload failed.'));
                        console.error('Image upload failed', err);
                    }
                });
        } else {
            this.submitSave(buildRequest(fv.imageUrl || undefined));
        }
    }

    /**
     * Builds the link payload for one selected modifier group.
     *
     * When editing, the existing link is reused so its position, required
     * override and active flag survive the save. The form only offers a
     * checkbox, so hardcoding `requiredOverride: false` here silently cleared any
     * override that had been set on this item, and `displayOrder: 0` threw the
     * groups out of their configured order on every save.
     *
     * A brand-new link sends no displayOrder: the backend appends it in the order
     * the groups are listed, which is the order the checkboxes appear.
     */
    private modifierGroupPayload(modifierGroupId: number) {
        const existing = this.editingProduct?.modifierGroups
            ?.find(link => link.modifierGroupId === modifierGroupId);

        return {
            modifierGroupId,
            displayOrder: existing?.displayOrder,
            requiredOverride: existing?.requiredOverride ?? undefined,
            isActive: existing?.isActive ?? true,
        };
    }

    private submitSave(request: MenuItemCreateRequest | MenuItemUpdateRequest): void {
        this.saving.set(true);
        this.saveError.set(null);

        const call = this.editingProductId !== null
            ? this.productService.update(this.editingProductId, request as MenuItemUpdateRequest)
            : this.productService.create(request as MenuItemCreateRequest);

        call
            .pipe(finalize(() => this.saving.set(false)))
            .subscribe({
                next: () => {
                    this.closeModal();
                    this.loadProducts();
                },
                error: (err) => {
                    this.saveError.set(this.extractError(err, 'Could not save the menu item.'));
                    console.error('Failed to save product', err);
                }
            });
    }

    // ---- Delete ----

    openDeleteConfirm(product: MenuItem): void {
        this.deletingProduct = product;
        this.deleteConfirmModal.nativeElement.showModal();
    }

    closeDeleteConfirm(): void {
        this.deletingProduct = null;
        this.deleteConfirmModal.nativeElement.close();
    }

    confirmDelete(): void {
        if (!this.deletingProduct) return;
        this.productService.delete(this.deletingProduct.id).subscribe({
            next: () => {
                this.closeDeleteConfirm();
                this.loadProducts();
            },
            error: (err) => {
                this.closeDeleteConfirm();
                this.listError.set(this.extractError(err, 'Could not delete the menu item.'));
                console.error('Failed to delete product', err);
            }
        });
    }

    // ---- Retire toggle ----

    /**
     * Retires or restores an item tenant-wide.
     *
     * Sends the full item because the endpoint is a PUT, and deliberately
     * omits displayOrder: the backend treats a missing position as "leave it
     * alone", so toggling retirement never renumbers the category.
     */
    toggleActive(product: MenuItem, isActive: boolean): void {
        this.productService.update(product.id, {
            categoryId: product.category.id,
            localizedNames: product.localizedNames,
            localizedDescriptions: product.localizedDescriptions,
            basePrice: product.basePrice,
            imageUrl: product.imageUrl ?? undefined,
            isActive,
            modifierGroups: product.modifierGroups?.map(mg => ({
                modifierGroupId: mg.modifierGroupId,
                displayOrder: mg.displayOrder,
                requiredOverride: mg.requiredOverride ?? false,
                isActive: mg.isActive,
            })) ?? [],
        }).subscribe({
            next: () => this.loadProducts(),
            error: (err) => {
                this.listError.set(this.extractError(err, 'Could not update availability.'));
                console.error('Failed to toggle availability', err);
            }
        });
    }

    // ---- Helpers ----

    formatPrice(price: number): string {
        // Up to two decimals, never rounded away: base_price is NUMERIC(19,2) and
        // a 12.50 item used to display as "13", which is the price the owner is
        // being shown for their own menu.
        return new Intl.NumberFormat('en-MM', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 2,
        }).format(price);
    }

    /**
     * Pulls the server's `message` field out of an error response so the user
     * sees something actionable instead of "an unexpected error occurred".
     */
    private extractError(err: unknown, fallback: string): string {
        return extractErrorMessage(err, fallback);
    }
}
