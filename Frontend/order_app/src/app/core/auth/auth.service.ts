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
