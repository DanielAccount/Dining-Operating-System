import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../../environment/environment";
import { Observable } from "rxjs";
import {
    BranchMenuItemRow,
    BranchMenuItemUpsertRequest,
    BranchMenuItemBulkUpsertRequest,
} from "../models/products_model";

/**
 * Branch pricing / availability rows.
 *
 * There is deliberately no listBranches() here: the branch list is app-wide
 * state (the sidebar picker, and every branch-scoped screen), so it lives in
 * BranchContextService rather than being fetched once per screen.
 */
@Injectable({ providedIn: 'root' })
export class BranchPricingService {

    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/branches`;

    getRows(branchId: number): Observable<BranchMenuItemRow[]> {
        return this.http.get<BranchMenuItemRow[]>(`${this.baseUrl}/${branchId}/menu-items`);
    }

    upsert(branchId: number, menuItemId: number, request: BranchMenuItemUpsertRequest): Observable<BranchMenuItemRow> {
        return this.http.put<BranchMenuItemRow>(`${this.baseUrl}/${branchId}/menu-items/${menuItemId}`, request);
    }

    bulkUpsert(branchId: number, request: BranchMenuItemBulkUpsertRequest): Observable<BranchMenuItemRow[]> {
        return this.http.put<BranchMenuItemRow[]>(`${this.baseUrl}/${branchId}/menu-items`, request);
    }

    reset(branchId: number, menuItemId: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${branchId}/menu-items/${menuItemId}`);
    }
}
