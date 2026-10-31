import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environment/environment";
import { PublicTableMenuResponse } from "./storefront_model";

/** Cart -> request body. Amounts never travel: the server prices the cart. */
export interface OrderSubmissionPayload {
    idempotencyKey: string;
    items: { menuItemId: number; quantity: number; modifiers?: { modifierOptionId: number }[] }[];
    customerNote?: string;
    customerPhoneNumber?: string;
}

/** An order, as the public endpoints return it (mirrors backend OrderResponse). */
export interface OrderSummary {
    id: number;
    status: string;
    orderType: string;
    source: string;
    tableNumber: string | null;
    sessionCode: string | null;
    customerNote: string | null;
    invoiceNumber: string | null;
    subtotal: number;
    taxAmount: number;
    discountAmount: number;
    serviceCharge: number;
    finalAmount: number;
    createdAt: string;
    items: {
        id: number;
        itemNameSnapshot: Record<string, string>;
        quantity: number;
        unitPrice: number;
        lineTotal: number;
        itemStatus: string;
        modifiers: { id: number; name: string; price: number }[];
    }[];
}

/**
 * The customer's one call to the API: everything the phone needs to render
 * the storefront, in a single round trip (whose menu, which table, the
 * availability-filtered menu itself).
 *
 * No Authorization header is needed or sent by callers - this service is hit
 * from the public route by anonymous phones. If a staff member happens to
 * open a table link in the same browser, the auth interceptor attaches a
 * token; the backend's /api/public/** is permitAll, so it is simply ignored.
 *
 * A dead slug answers 404 with a generic "Menu not found" by design - the
 * storefront shows its not-found state and never explains which business,
 * branch or table the code belonged to.
 */
@Injectable({ providedIn: 'root' })
export class StorefrontService {

    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/public/tables`;

    menu(slug: string): Observable<PublicTableMenuResponse> {
        return this.http.get<PublicTableMenuResponse>(`${this.baseUrl}/${slug}/menu`);
    }

    /** Submit the cart. 201 = placed; 409 = the kitchen's reality moved (read the message); 404 = dead code. */
    submitOrder(slug: string, payload: OrderSubmissionPayload): Observable<OrderSummary> {
        return this.http.post<OrderSummary>(`${this.baseUrl}/${slug}/orders`, payload);
    }

    /** The table's orders for a phone holding the session code. */
    orders(slug: string, sessionCode: string): Observable<OrderSummary[]> {
        return this.http.get<OrderSummary[]>(`${this.baseUrl}/${slug}/orders`, {
            params: { sessionCode },
        });
    }
}
