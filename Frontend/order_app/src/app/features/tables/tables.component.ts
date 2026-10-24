import {
    ChangeDetectorRef,
    Component,
    ElementRef,
    OnDestroy,
    ViewChild,
    effect,
    inject,
    signal,
    untracked,
} from "@angular/core";
import { FormsModule } from "@angular/forms";
import { BranchContextService } from "../../core/branch/branch-context.service";
import { AuthService } from "../../core/auth/auth.service";
import { TablesService } from "./tables_service";
import { TableResponse } from "./tables_model";
import { extractErrorMessage } from "../../shared/http/error-message";
import { HasPermissionDirective } from "../../shared/directives/has_permission_directuve";
import * as QRCode from "qrcode";

/** One printable table tent: a number and its QR image, nothing else. */
interface PrintTent {
    tableNumber: string;
    qr: string;
}

/**
 * Table management for the active branch (Phase 3, Milestone 4).
 *
 * This is the owner's answer to "how do I get a QR code onto my tables":
 * add a table, the backend mints the slug, this screen renders the QR image
 * client-side from the server-assembled qrUrl - so there is no image
 * pipeline anywhere, and switching a deployment's base URL never invalidates
 * a database row, only what a printer would print.
 *
 * Reads are TABLE_VIEW (waiters look up tables and may re-print a damaged
 * tent); every write is TABLE_MANAGE and hidden behind the permission
 * directive so read-only roles never see a button that would 403.
 */
@Component({
    selector: 'app-tables',
    standalone: true,
    imports: [FormsModule, HasPermissionDirective],
    templateUrl: './tables.component.html',
})
export class TablesComponent implements OnDestroy {

    private readonly tablesService = inject(TablesService);
    private readonly branchContext = inject(BranchContextService);
    private readonly auth = inject(AuthService);
    private readonly cdr = inject(ChangeDetectorRef);

    // Branch state is app-wide (the sidebar picker writes it too); this page
    // only follows it, exactly like the branch pricing screen.
    readonly branches = this.branchContext.branches;
    readonly selectedBranchId = this.branchContext.selectedBranchId;
    readonly isTenantWide = this.branchContext.isTenantWide;
    readonly businessName = this.auth.businessName;

    // Data
    tables = signal<TableResponse[]>([]);
    qrThumbs = signal<Record<number, string>>({});

    // UI state
    loading = signal(false);
    error = signal<string | null>(null);
    successMessage = signal<string | null>(null);

    // Add / rename dialog
    @ViewChild('addEditDialog') addEditDialog!: ElementRef<HTMLDialogElement>;
    editingTable = signal<TableResponse | null>(null);
    tableNumberInput = '';

    // QR viewer dialog
    @ViewChild('qrDialog') qrDialog!: ElementRef<HTMLDialogElement>;
    qrTable = signal<TableResponse | null>(null);
    qrImage = signal('');

    // Confirm dialog (delete / regenerate - both are destructive to printed codes)
    @ViewChild('confirmDialog') confirmDialog!: ElementRef<HTMLDialogElement>;
    confirmTitle = '';
    confirmMessage = '';
    confirmButtonLabel = '';
    private confirmAction: (() => void) | null = null;

    // Print sheet - content only exists while a print is in flight
    printTents = signal<PrintTent[]>([]);

    /** Guards a slow branch-A response from overwriting branch-B's list. */
    private loadToken = 0;

    private readonly onAfterPrint = () => this.printTents.set([]);

    constructor() {
        // Tables follow the active branch, whichever screen changed it.
        effect(() => {
            const branchId = this.selectedBranchId();
            untracked(() => this.load(branchId));
        });
        window.addEventListener('afterprint', this.onAfterPrint);
    }

    ngOnDestroy(): void {
        window.removeEventListener('afterprint', this.onAfterPrint);
    }

    selectBranch(branchId: number | null): void {
        this.branchContext.select(branchId);
    }

    // ---- loading ----

    load(branchId: number | null): void {
        const token = ++this.loadToken;

        if (branchId == null) {
            this.tables.set([]);
            this.qrThumbs.set({});
            return;
        }

        this.loading.set(true);
        this.error.set(null);

        this.tablesService.list(branchId).subscribe({
            next: data => {
                if (token !== this.loadToken) return; // superseded by a newer load
                this.tables.set(data);
                this.loading.set(false);
                this.generateThumbs(data);
                this.cdr.detectChanges();
            },
            error: err => {
                if (token !== this.loadToken) return;
                this.loading.set(false);
                this.tables.set([]);
                this.qrThumbs.set({});
                this.error.set(extractErrorMessage(err, 'Failed to load tables.'));
            }
        });
    }

    /**
     * QR thumbnails are generated locally from each table's qrUrl - the
     * backend never ships images. Cheap (a few ms per code) and cached by
     * the browser as a data URL.
     */
    private generateThumbs(list: TableResponse[]): void {
        Promise.all(
            list.map(t => this.qrDataUrl(t.qrUrl, 200).then(d => [t.id, d] as const))
        ).then(entries => this.qrThumbs.set(Object.fromEntries(entries) as Record<number, string>));
    }

    private qrDataUrl(url: string, width: number): Promise<string> {
        return QRCode.toDataURL(url, { width, margin: 2 });
    }

    // ---- add / rename ----

    openCreate(): void {
        this.editingTable.set(null);
        this.tableNumberInput = '';
        this.addEditDialog.nativeElement.showModal();
    }

    openEdit(table: TableResponse): void {
        this.editingTable.set(table);
        this.tableNumberInput = table.tableNumber;
        this.addEditDialog.nativeElement.showModal();
    }

    saveTable(): void {
        const branchId = this.selectedBranchId();
        const number = this.tableNumberInput.trim();
        if (branchId == null || !number) return;

        const editing = this.editingTable();
        const call = editing
            ? this.tablesService.update(branchId, editing.id, number)
            : this.tablesService.create(branchId, number);

        call.subscribe({
            next: () => {
                this.addEditDialog.nativeElement.close();
                this.successMessage.set(editing
                    ? `Table renamed to "${number}" — its QR code is unchanged, printed codes keep working.`
                    : `Table "${number}" created. Its QR code is ready to print.`);
                this.load(branchId);
            },
            error: err => this.error.set(extractErrorMessage(err, 'Failed to save the table.'))
        });
    }

    // ---- delete / regenerate (both confirmed) ----

    askDelete(table: TableResponse): void {
        this.confirmTitle = 'Delete table';
        this.confirmMessage =
            `Delete table "${table.tableNumber}"? Its QR code stops working immediately - ` +
            `customers scanning the old sticker will see "Menu not found".`;
        this.confirmButtonLabel = 'Delete';
        this.confirmAction = () => this.deleteTable(table);
        this.confirmDialog.nativeElement.showModal();
    }

    askRegenerate(table: TableResponse): void {
        this.confirmTitle = 'Regenerate QR code';
        this.confirmMessage =
            `Mint a new QR code for table "${table.tableNumber}"? Every code printed for this ` +
            `table before now stops working, so the table must be re-printed and re-stickered. ` +
            `Use this only when a code is compromised or the sticker is being replaced anyway.`;
        this.confirmButtonLabel = 'Regenerate';
        this.confirmAction = () => this.regenerateTable(table);
        this.confirmDialog.nativeElement.showModal();
    }

    confirmProceed(): void {
        this.confirmDialog.nativeElement.close();
        const action = this.confirmAction;
        this.confirmAction = null;
        action?.();
    }

    private deleteTable(table: TableResponse): void {
        const branchId = this.selectedBranchId();
        if (branchId == null) return;

        this.tablesService.remove(branchId, table.id).subscribe({
            next: () => {
                this.successMessage.set(`Table "${table.tableNumber}" deleted.`);
                this.load(branchId);
            },
            error: err => this.error.set(extractErrorMessage(err, 'Failed to delete the table.'))
        });
    }

    private regenerateTable(table: TableResponse): void {
        const branchId = this.selectedBranchId();
        if (branchId == null) return;

        this.tablesService.regenerateQr(branchId, table.id).subscribe({
            next: () => {
                this.successMessage.set(
                    `New QR code for "${table.tableNumber}" - print it and replace the sticker on the table.`);
                this.load(branchId);
            },
            error: err => this.error.set(extractErrorMessage(err, 'Failed to regenerate the QR code.'))
        });
    }

    // ---- QR viewer ----

    openQr(table: TableResponse): void {
        this.qrTable.set(table);
        this.qrImage.set('');
        this.qrDialog.nativeElement.showModal();
        this.qrDataUrl(table.qrUrl, 480).then(d => this.qrImage.set(d));
    }

    copyUrl(): void {
        const url = this.qrTable()?.qrUrl;
        if (!url) return;

        navigator.clipboard.writeText(url).then(
            () => this.successMessage.set('QR link copied to clipboard.'),
            () => this.error.set('Could not copy the link. Select the text and copy it manually.')
        );
    }

    downloadQr(): void {
        const table = this.qrTable();
        if (!table || !this.qrImage()) return;

        const link = document.createElement('a');
        link.href = this.qrImage();
        link.download = `qr-table-${table.tableNumber}.png`;
        link.click();
    }

    // ---- printing ----

    printOne(table: TableResponse): void {
        this.printTables([table]);
    }

    printAll(): void {
        this.printTables(this.tables());
    }

    /**
     * Builds the print sheet, then hands it to the browser. The sheet lives
     * in the template (styled from the global stylesheet, which is where a
     * whole-page concern like print layout belongs); a beat of setTimeout
     * lets the layout settle before the print dialog captures it.
     */
    private printTables(list: TableResponse[]): void {
        if (list.length === 0) return;

        Promise.all(list.map(async t => ({
            tableNumber: t.tableNumber,
            qr: await this.qrDataUrl(t.qrUrl, 600),
        }))).then(tents => {
            this.printTents.set(tents);
            this.cdr.detectChanges();
            setTimeout(() => window.print(), 100);
        });
    }
}
