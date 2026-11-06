import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environment/environment';
import { KitchenItem } from './kitchen.models';

/**
 * Phase 5 kitchen-display API.
 *
 * The branch id follows the same rule as every staff screen: branch-pinned
 * chefs always operate on their own branch, while a tenant-wide owner names
 * the branch explicitly.
 */
@Injectable({ providedIn: 'root' })
export class KitchenService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/branches`;

  /** Active lines on the branch's kitchen board, oldest first. */
  list(branchId: number): Observable<KitchenItem[]> {
    return this.http.get<KitchenItem[]>(`${this.baseUrl}/${branchId}/kds`);
  }

  markPreparing(branchId: number, itemId: number): Observable<KitchenItem> {
    return this.http.post<KitchenItem>(
      `${this.baseUrl}/${branchId}/kds/items/${itemId}/preparing`,
      {},
    );
  }

  markReady(branchId: number, itemId: number): Observable<KitchenItem> {
    return this.http.post<KitchenItem>(
      `${this.baseUrl}/${branchId}/kds/items/${itemId}/ready`,
      {},
    );
  }

  markServed(branchId: number, itemId: number): Observable<KitchenItem> {
    return this.http.post<KitchenItem>(
      `${this.baseUrl}/${branchId}/kds/items/${itemId}/served`,
      {},
    );
  }
}
