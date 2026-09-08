import { Component, ElementRef, inject, OnInit, signal, ViewChild } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { CategoryService } from "../categories/category_service";
import { Category } from "../models/categories_model";

@Component({
    selector: 'products_management',
    standalone: true,
    imports: [ReactiveFormsModule], 
    templateUrl: './products.html'
})
export class ProductsComponent implements OnInit {

    private readonly fb = inject(FormBuilder);
    private readonly categoryService = inject(CategoryService);

    @ViewChild('productModal') productModal!: ElementRef<HTMLDialogElement>;

    // Signal to store category list
    categories = signal<Category[]>([]);

    productForm = this.fb.group({
        name: ['', Validators.required],
        myanmarName: ['', Validators.required],
        categoryId: ['', Validators.required],
        price: [0, [Validators.required, Validators.min(0)]],
        imageUrl: [''],
        descriptionEn: [''],
        descriptionMm: [''],
    })

    ngOnInit(): void {
        this.loadCategories();
    }

    private loadCategories(): void {
        this.categoryService.getAll().subscribe({
            next: (data) => this.categories.set(data),
            error: (err) => console.error('Failed to load categories', err)
        });
    }

    openModal(): void {
        this.productModal.nativeElement.showModal();
    }

    closeModal(): void {
        this.productModal.nativeElement.close();
        this.productForm.reset({ price: 0 });
    }

    saveProduct(): void {
        if (this.productForm.invalid) {
            this.productForm.markAllAsTouched();
            return;
        }

        const formValue = this.productForm.getRawValue();
        console.log('Save product:', formValue);
        // TODO: Call product service to create product
        // this.productService.create(formValue).subscribe(...)
        this.closeModal();
    }

}