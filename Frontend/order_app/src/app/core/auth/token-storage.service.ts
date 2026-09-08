import { Injectable } from "@angular/core";


// This stores the JWT in localStorage.
// For local development, this is fine.
// Later, we can discuss more secure browser storage strategies.

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