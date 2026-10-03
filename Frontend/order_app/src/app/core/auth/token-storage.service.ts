import { Injectable } from "@angular/core";


/**
 * Where the JWT lives between page loads.
 *
 * ── SECURITY NOTE (accepted for MVP, must be revisited before production) ──
 * localStorage is readable by any JavaScript running on the origin, so a single
 * XSS anywhere in the app hands an attacker a full-tenant session token that
 * works until it expires (see JwtService for how long). There is no cookie, no
 * httpOnly protection and no revocation, so the mitigations today are "no
 * untrusted input reaches the DOM" rather than anything structural.
 *
 * The standard fix is a short-lived access token in memory plus a refresh token
 * in an httpOnly, Secure, SameSite=Strict cookie - which needs backend support
 * (refresh + revoke endpoints) and so is a Phase 7/8 decision, not a Phase 2 one.
 */
@Injectable({
    providedIn:'root'
})
export class TokenStorageService {
    private readonly TOKEN_KEY = 'dining_access_token';

    saveToken(token: string): void {
        localStorage.setItem(this.TOKEN_KEY,token);
    }

    getToken(): string | null {
        return localStorage.getItem(this.TOKEN_KEY);
    }

    clearToken(): void {
        localStorage.removeItem(this.TOKEN_KEY);
    }
}
