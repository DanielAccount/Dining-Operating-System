import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../../environment/environment";
import { PublicTableMenuResponse } from "./storefront_model";

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
}
