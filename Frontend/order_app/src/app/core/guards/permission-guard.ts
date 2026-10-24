import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthService } from "../auth/auth.service";

export function permissionGuard(permission: string): CanActivateFn {
    return () => {
        const authService = inject(AuthService);
        const router = inject(Router);
        if (authService.hasPermission(permission)){
            return true;
        }

        // User is authenticated but lacks this permission — don't force
        // re-login, and don't dump them on a random screen: send them to
        // their own landing page (dashboard/kitchen/orders/payments).
        // authService.landingUrl() only ever returns routes the user's
        // permissions already cover, so this cannot loop.
        return router.parseUrl(authService.landingUrl());
    };
}