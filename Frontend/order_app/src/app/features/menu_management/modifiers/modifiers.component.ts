import { ChangeDetectorRef, Component, ElementRef, inject, OnInit, ViewChild } from "@angular/core";
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { ModifierService } from "./modifer_service";
import { ModifierGroup } from "../models/modifiers_models";
import { extractErrorMessage } from "../../../shared/http/error-message";

@Component({
    selector: 'modifiers_management',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './modifiers.html'
})
export class ModifiersComponent implements OnInit {
    private readonly modifierService = inject(ModifierService);
    private readonly fb = inject(FormBuilder);
    private readonly cdr = inject(ChangeDetectorRef);

    @ViewChild('modifiersDialog') modalRef!: ElementRef<HTMLDialogElement>;

    //  Store the fetched modifier groups from loadModifiers()
    modifiers: ModifierGroup[] = [];
    isSaving = false;
    editingModifierId: number | null = null;

    /** Page-level failure (list could not be loaded). */
    listError: string | null = null;
    /** Failure of the save/delete currently in the modal, shown inline. */
    saveError: string | null = null;

    modifierForm = this.fb.group({
        englishName: ['', Validators.required],
        // Myanmar name is optional: the backend requires ONE localized name, not
        // two, and the option payload below omits the key when it is blank.
        myanmarName: [''],
        isRequired: ['no', Validators.required],
        minSelect: [0, [Validators.required, Validators.min(0)]],
        maxSelect: [1, [Validators.required, Validators.min(1)]],
        options: this.fb.array([])  // Dynamic rows
    })

    ngOnInit(): void {
        this.addOptionRow(); // Add one empty row by default so the UI isn't empty
        this.loadModifiers();
    }

    // Helper to get the FormArray
    get optionsArray(): FormArray {
        return this.modifierForm.get('options') as FormArray;
    }

    /**
     * Factory for a single option row.
     *
     * `id` and `isAvailable` are part of the row, not decoration: the backend
     * reconciles options BY ID (an option that arrives without one is treated as
     * new, and the old row is deleted). Dropping them here meant that saving a
     * modifier group silently gave every option a brand-new ID and destroyed the
     * old rows - harmless-looking until Phase 4 order lines reference
     * modifier_option_id, at which point it corrupts order history.
     */
    private buildOptionRow(
        id: number | null = null,
        en = '',
        mm = '',
        price: number = 0,
        isAvailable = true,
    ) {
        return this.fb.group({
            id: [id as number | null],
            englishName: [en, Validators.required],
            myanmarName: [mm],
            extraPrice: [price, [Validators.required, Validators.min(0)]],
            isAvailable: [isAvailable],
        });
    }

    // Method to add a new option row
    addOptionRow(): void {
        this.optionsArray.push(this.buildOptionRow());
    }

    removeOption(index: number): void {
        this.optionsArray.removeAt(index);
    }

    loadModifiers(): void {
        this.modifierService.getAll().subscribe({
            next: (modifiers) => {
                this.modifiers = modifiers;
                this.listError = null;
                this.cdr.markForCheck();
            },
            error: (err) => {
                this.listError = extractErrorMessage(err, 'Could not load modifier groups.');
                console.error('Failed to load modifiers', err);
            },
        });
    }

    openCreateModal(): void {
        this.editingModifierId = null;
        this.saveError = null;
        this.modifierForm.reset({
            englishName: '',
            myanmarName: '',
            isRequired: 'no',
            minSelect: 0,
            maxSelect: 1
        });

        this.optionsArray.clear();
        this.addOptionRow();
        this.modalRef.nativeElement.showModal();
    }

    openEditModal(modifier: ModifierGroup): void {
        this.editingModifierId = modifier.id;
        this.saveError = null;
        this.modifierForm.patchValue({
            englishName: modifier.localizedNames.en || '',
            myanmarName: modifier.localizedNames.mm || '',
            isRequired: modifier.isRequired ? 'yes' : 'no',
            minSelect: modifier.minSelect,
            maxSelect: modifier.maxSelect
        });

        // New FormGroup instances per row, with the option's id and availability
        // carried across, so saving updates rows in place instead of replacing
        // them.
        this.optionsArray.clear();
        const opts = modifier.options ?? [];
        for (const opt of opts) {
            this.optionsArray.push(this.buildOptionRow(
                opt.id,
                opt.localizedNames.en ?? '',
                opt.localizedNames.mm ?? '',
                opt.extraPrice ?? 0,
                opt.isAvailable ?? true,
            ));
        }
        if (opts.length === 0) {
            this.addOptionRow();
        }

        this.modalRef.nativeElement.showModal();
    }

    closeModal(): void {
        this.modalRef.nativeElement.close();
        this.editingModifierId = null;
        this.saveError = null;
        this.modifierForm.reset({
            isRequired: 'no',
            minSelect: 0,
            maxSelect: 1
        });
        this.optionsArray.clear();
        this.addOptionRow();
    }

    saveModifier(): void {
        if (this.modifierForm.invalid) {
            this.modifierForm.markAllAsTouched();
            return;
        }

        this.isSaving = true;
        this.saveError = null;

        const val = this.modifierForm.getRawValue();

        const payload = {
            localizedNames: localizedNames(val.englishName ?? '', val.myanmarName ?? ''),
            isRequired: val.isRequired === 'yes',
            minSelect: val.minSelect!,
            maxSelect: val.maxSelect!,
            // No id -> the backend creates the row. An id -> it updates that row
            // in place. Never omit the id, or every option is recreated.
            options: (val.options ?? []).map((opt: any) => ({
                ...(opt.id != null ? { id: opt.id } : {}),
                localizedNames: localizedNames(opt.englishName ?? '', opt.myanmarName ?? ''),
                extraPrice: opt.extraPrice,
                isAvailable: opt.isAvailable ?? true,
            })),
            // isActive is deliberately omitted: null on the request means
            // "leave it alone", so editing a group cannot silently un-retire it.
        };

        const request$ = this.editingModifierId !== null
            ? this.modifierService.update(this.editingModifierId, payload)
            : this.modifierService.create(payload);

        request$.subscribe({
            next: () => {
                this.closeModal();
                this.loadModifiers();
                this.isSaving = false;
            },
            error: (err) => {
                console.error('Failed to save', err);
                this.saveError = extractErrorMessage(err, 'Could not save the modifier group.');
                this.isSaving = false;
            }
        });
    }

    deleteModifier(modifier: ModifierGroup): void {
        const name = modifier.localizedNames.en || modifier.localizedNames.mm || 'this modifier group';
        if (!confirm(`Delete "${name}"? This cannot be undone.`)) {
            return;
        }

        this.modifierService.delete(modifier.id).subscribe({
            next: () => {
                this.listError = null;
                this.loadModifiers();
            },
            error: (err) => {
                console.error('Failed to delete', err);
                // 409 means the group is still attached to menu items - the
                // backend says so in the message, and the fix (detach it first)
                // is not something we can do for the user from here.
                this.listError = extractErrorMessage(
                    err,
                    'Could not delete the modifier group.');
            }
        });
    }
}

/**
 * Builds the localized-name map the API expects: at least one non-blank entry.
 * A blank language is omitted rather than sent as "", which fails validation.
 * The English name is required by the form, so the map is never empty when this
 * is called from saveModifier().
 */
function localizedNames(en: string, mm: string): { en?: string; mm?: string } {
    const names: { en?: string; mm?: string } = {};
    if (en.trim()) names.en = en.trim();
    if (mm.trim()) names.mm = mm.trim();
    return names;
}
