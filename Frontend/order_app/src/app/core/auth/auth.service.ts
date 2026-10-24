import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { computed, inject, Injectable, signal } from "@angular/core";
import { LoginResponse, MeResponse } from "./auth.model";
import { catchError, finalize, map, Observable, tap, of, throwError } from "rxjs";
import { TokenStorageService } from "./token-storage.service";
import { environment } from "../../../environment/environment";
import { Router } from "@angular/router";

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly http = inject(HttpClient);
    private readonly tokenStorage = inject(TokenStorageService);
    private router = inject(Router); 

    readonly currentUser = signal<MeResponse | null>(null);
    readonly isAuthenticated = signal<boolean>(false);
    readonly loading = signal<boolean>(false);
    readonly error = signal<string | null>(null);

    // Derived read-only signals used by templates (sidebar, store page, etc.)
    readonly currentRole = computed(() => this.currentUser()?.role ?? '');
    readonly businessName = computed(() => this.currentUser()?.tenant?.businessName ?? '');
    readonly businessAddress = computed(() => this.currentUser()?.tenant?.address ?? '');
    readonly displayName = computed(() => this.currentUser()?.displayName ?? this.currentUser()?.username ?? '');

    private sessionChecked = false;

    login(email:string, password: string): Observable<MeResponse> {
        this.loading.set(true);
        this.error.set(null);

        const body = {
            email,
            password
        };

        return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, body)
            .pipe(
                tap(response => {
                    this.tokenStorage.saveToken(response.accessToken);
                    this.currentUser.set(response.user)
                    this.isAuthenticated.set(true)
                    this.sessionChecked = true;
                }),
                map(response => response.user),
                catchError(error => {
                    this.error.set(this.getErrorMessage(error));
                    return throwError(() => error);
                }),
                finalize(() => {
                    this.loading.set(false);
                })

            )
    }


    restoreSession() : Observable<boolean> {
        if (this.sessionChecked) {
            return of(this.isAuthenticated());
        }

        const token = this.tokenStorage.getToken();

        if (!token) {
            this.sessionChecked = true;
            return of(false);
        }

        return this.http
            .get<MeResponse>(`${environment.apiUrl}/me`)
            .pipe(
                tap(me => {
                this.currentUser.set(me);
                this.isAuthenticated.set(true);
            }),
                map(() => true),
                catchError(() => {
                    this.tokenStorage.clearToken();
                    this.currentUser.set(null);
                    this.isAuthenticated.set(false);
                    return of(false);
                }),
                finalize(() => {
                    this.sessionChecked = true;
                })
            );
    }

    logout(): void {
        this.tokenStorage.clearToken();
        this.currentUser.set(null);
        this.isAuthenticated.set(false);
        this.sessionChecked = false;
        this.router.navigateByUrl('/login');

    }

    hasPermission(permission: string): boolean {
        const user = this.currentUser();

        if (!user) {
            return false;
        }

        return user.permissions.includes(permission);
    }

    /**
     * The page this user should land on after login (and whenever they
     * hit /app or a route they lack permission for).
     *
     * Ordered checks — each branch uniquely matches one seeded role,
     * and stays correct for custom roles too (they simply fall into the
     * first branch whose permission they hold):
     *
     *   DASHBOARD_VIEW         → OWNER, MANAGER
     *   KDS_VIEW               → CHEF
     *   SERVICE_REQUEST_HANDLE → WAITER   (orders = their workspace)
     *   PAYMENT_VIEW           → CASHIER
     *
     * Every target is guarded by a permission that role provably has,
     * so this can never bounce a user into a route they can't open
     * (no redirect loops). Zero-permission accounts get /app/no-access.
     */
    landingUrl(): string {
        if (this.hasPermission('DASHBOARD_VIEW'))  return '/app/dashboard';
        if (this.hasPermission('KDS_VIEW'))        return '/app/kitchen';

        // Waiter: SERVICE_REQUEST_HANDLE marks the role as waiter, but the
        // orders page itself needs ORDER_VIEW — require both so a custom
        // role holding only one of them can't be sent somewhere it can't go.
        if (this.hasPermission('SERVICE_REQUEST_HANDLE') && this.hasPermission('ORDER_VIEW')) {
            return '/app/orders';
        }

        if (this.hasPermission('PAYMENT_VIEW'))    return '/app/payments';

        return '/app/no-access';
    }


    private getErrorMessage(error: HttpErrorResponse): string {
        if (error.status === 401) {
            return 'Invalid email or password.';
        }

        return 'Login failed. Please try again.';
    }

    
}

// This service handles:
// login
// logout
// restoring session after page refresh
// current user state
// permissions check
