import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environment/environment";
import { TableResponse } from "./tables_model";

/**
 * Branch-scoped table management (Phase 3).
 *
 * Same conventions as BranchPricingService: no listBranches() here - the
 * active branch is app-wide state in BranchContextService - and every call
 * names the branch because the backend routes by it
 * (api/branches/{branchId}/tables).
 *
 * Permission enforcement happens server-side (TABLE_VIEW to read,
 * TABLE_MANAGE to write); the UI merely hides buttons it knows would 403.
 */
@Injectable({ providedIn: 'root' })
export class TablesService {

    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/branches`;

    list(branchId: number): Observable<TableResponse[]> {
        return this.http.get<TableResponse[]>(`${this.baseUrl}/${branchId}/tables`);
    }

    create(branchId: number, tableNumber: string): Observable<TableResponse> {
        return this.http.post<TableResponse>(`${this.baseUrl}/${branchId}/tables`, { tableNumber });
    }

    update(branchId: number, tableId: number, tableNumber: string): Observable<TableResponse> {
        return this.http.put<TableResponse>(`${this.baseUrl}/${branchId}/tables/${tableId}`, { tableNumber });
    }

    remove(branchId: number, tableId: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${branchId}/tables/${tableId}`);
    }

    /**
     * Mints a replacement QR code. Orphans every previously printed code for
     * the table - the UI must always confirm before calling this.
     */
    regenerateQr(branchId: number, tableId: number): Observable<TableResponse> {
        return this.http.post<TableResponse>(`${this.baseUrl}/${branchId}/tables/${tableId}/regenerate-qr`, {});
    }
}
