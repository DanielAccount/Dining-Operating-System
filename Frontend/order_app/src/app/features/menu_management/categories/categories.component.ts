import { Component, ElementRef, inject, OnInit, ViewChild, ChangeDetectorRef } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategoryService } from './category_service';
import { Category } from '../models/categories_model';

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

  categoryForm = this.fb.group({
    englishName: ['', Validators.required],
    myanmarName: ['', Validators.required],
    displayOrder: [1, [Validators.required, Validators.min(1)]]
  });

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.categoryService.getAll().subscribe({
      next: (categories) => {
        this.categories = categories;
        this.cdr.detectChanges();
      },
      error: (error) => console.error('Failed to load categories', error)
    });
  }

  openCreateModal(): void {
    this.editingCategoryId = null;
    const nextOrder = this.categories.length + 1;
    this.categoryForm.reset({ displayOrder: nextOrder });
    this.categoryModal.nativeElement.showModal();
  }

  openEditModal(category: Category): void {
    this.editingCategoryId = category.id;
    this.categoryForm.patchValue({
      englishName: category.localizedNames.en || '',
      myanmarName: category.localizedNames.mm || '',
      displayOrder: category.displayOrder
    });
    this.categoryModal.nativeElement.showModal();
  }

  closeModal(): void {
    this.categoryModal.nativeElement.close();
    this.editingCategoryId = null;
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
        en: formValue.englishName!,
        mm: formValue.myanmarName!
      },
      displayOrder: formValue.displayOrder!
    };

    if (this.editingCategoryId !== null) {
      this.categoryService.update(this.editingCategoryId, payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadCategories(); // Reload all categories to catch shifted orders
        },
        error: (error) => console.error('Failed to update category', error)
      });
    } else {
      this.categoryService.create(payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadCategories(); // Reload all categories to catch shifted orders
        },
        error: (error) => console.error('Failed to create category', error)
      });
    }
  }
}