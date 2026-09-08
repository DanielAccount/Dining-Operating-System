import { HttpHandlerFn, HttpRequest, HttpErrorResponse } from "@angular/common/http";
import { TokenStorageService } from "../auth/token-storage.service";
import { inject } from "@angular/core";
import { Router } from "@angular/router";
import { catchError, throwError } from "rxjs";

export function authInterceptor(
    req: HttpRequest<unknown>,
    next: HttpHandlerFn
) {
    const tokenStorage = inject(TokenStorageService);
    const router = inject(Router);
    const token = tokenStorage.getToken();
    let request = req;

    if (token) {
        request = req.clone({
        setHeaders: {
            Authorization: `Bearer ${token}`
        }
        });
    }

    return next(request)
        .pipe(
            catchError((error: HttpErrorResponse) => {
            const isLoginRequest = req.url.includes('/auth/login');

            if (error.status === 401 && !isLoginRequest) {
                tokenStorage.clearToken();
                router.navigateByUrl('/login');
            }

            return throwError(() => error);
            })
        );
        
}
   
