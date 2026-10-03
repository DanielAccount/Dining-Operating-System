import { ChangeDetectorRef, Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategoryService } from './category_service';
import { Category } from '../models/categories_model';
import { extractErrorMessage } from '../../../shared/http/error-message';

@Component({
  selector: 'categories_management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './categories.html',
})
export class CategoriesComponent implements OnInit {
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('categoryModal') categoryModal!: ElementRef<HTMLDialogElement>;

  categories: Category[] = [];
  editingCategoryId: number | null = null;
  saving = false;

  /** Failure loading the list. */
  readonly listError = signal<string | null>(null);
  /** Failure of the save in the modal - shown inline, not swallowed. */
  readonly saveError = signal<string | null>(null);

  categoryForm = this.fb.group({
    englishName: ['', Validators.required],
    // Optional: the API requires one localized name, not two.
    myanmarName: [''],
    displayOrder: [1, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.categoryService.getAll().subscribe({
      next: (categories) => {
        this.categories = categories;
        this.listError.set(null);
        this.cdr.detectChanges();
      },
      error: (error) => {
        // Previously only console.error, so a failed load looked like an empty menu.
        this.listError.set(extractErrorMessage(error, 'Could not load categories.'));
        console.error('Failed to load categories', error);
      },
    });
  }

  openCreateModal(): void {
    this.editingCategoryId = null;
    this.saveError.set(null);
    // The backend clamps out-of-range positions, so "length + 1" always lands
    // at the end even if the list changed since it loaded.
    this.categoryForm.reset({
      englishName: '',
      myanmarName: '',
      displayOrder: this.categories.length + 1,
    });
    this.categoryModal.nativeElement.showModal();
  }

  openEditModal(category: Category): void {
    this.editingCategoryId = category.id;
    this.saveError.set(null);
    this.categoryForm.patchValue({
      englishName: category.localizedNames.en || '',
      myanmarName: category.localizedNames.mm || '',
      displayOrder: category.displayOrder,
    });
    this.categoryModal.nativeElement.showModal();
  }

  closeModal(): void {
    this.categoryModal.nativeElement.close();
    this.editingCategoryId = null;
    this.saveError.set(null);
    this.categoryForm.reset({ displayOrder: 1 });
  }

  saveCategory(): void {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }

    const formValue = this.categoryForm.getRawValue();
    const payload = {
      localizedNames: {
        en: formValue.englishName!.trim(),
        ...(formValue.myanmarName?.trim() ? { mm: formValue.myanmarName.trim() } : {}),
      },
      displayOrder: formValue.displayOrder!,
    };

    this.saving = true;
    this.saveError.set(null);

    const request$ =
      this.editingCategoryId !== null
        ? this.categoryService.update(this.editingCategoryId, payload)
        : this.categoryService.create(payload);

    request$.subscribe({
      next: () => {
        this.saving = false;
        this.closeModal();
        // Reload: inserting or moving a category renumbers its neighbours, so
        // the local list is stale whether or not this row itself moved.
        this.loadCategories();
      },
      error: (error) => {
        this.saving = false;
        this.saveError.set(extractErrorMessage(error, 'Could not save the category.'));
        console.error('Failed to save category', error);
      },
    });
  }

  deleteCategory(category: Category): void {
    const name = category.localizedNames.en || category.localizedNames.mm || 'this category';
    if (!confirm(`Delete "${name}"? This cannot be undone.`)) {
      return;
    }

    this.categoryService.delete(category.id).subscribe({
      next: () => {
        this.listError.set(null);
        this.loadCategories();
      },
      error: (error) => {
        // 409: the category still has menu items. The backend's message says so.
        this.listError.set(extractErrorMessage(error, 'Could not delete the category.'));
        console.error('Failed to delete category', error);
      },
    });
  }
}
