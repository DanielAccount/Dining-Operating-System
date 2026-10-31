import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environment/environment";
import { Order, OrderDetail } from "./order.models";

/**
 * Staff-side order endpoints (Phase 4). Branch-scoped like every other
 * branch resource: the branch id in the path is mandatory for tenant-wide
 * users and ignored for branch-pinned staff, exactly as the backend rules.
 */
@Injectable({ providedIn: 'root' })
export class StaffOrderService {

    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/branches`;

    /** The branch's orders, newest first - the working list. */
    list(branchId: number): Observable<Order[]> {
        return this.http.get<Order[]>(`${this.baseUrl}/${branchId}/orders`);
    }

    /** Order + full status timeline. */
    detail(branchId: number, orderId: number): Observable<OrderDetail> {
        return this.http.get<OrderDetail>(`${this.baseUrl}/${branchId}/orders/${orderId}`);
    }

    /** A waiter/cashier places an order for a guest at a table. */
    createAtTable(
        branchId: number,
        tableId: number,
        body: {
            idempotencyKey: string;
            items: { menuItemId: number; quantity: number; modifiers?: { modifierOptionId: number }[] }[];
            customerNote?: string;
            customerPhoneNumber?: string;
        },
    ): Observable<Order> {
        return this.http.post<Order>(`${this.baseUrl}/${branchId}/tables/${tableId}/orders`, body);
    }

    /** PENDING -> ACCEPTED: confirms the ticket and mints the invoice number. */
    accept(branchId: number, orderId: number): Observable<Order> {
        return this.http.post<Order>(`${this.baseUrl}/${branchId}/orders/${orderId}/accept`, {});
    }

    /** Cancels with a reason recorded on the order. */
    cancel(branchId: number, orderId: number, reason: string): Observable<Order> {
        return this.http.post<Order>(`${this.baseUrl}/${branchId}/orders/${orderId}/cancel`, { reason });
    }
}
