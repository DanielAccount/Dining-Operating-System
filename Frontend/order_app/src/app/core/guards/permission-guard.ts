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

        // User is authenticated but lacks the permission — don't force re-login,
        // send them to the fallback page instead.
        return router.createUrlTree(['/management/store']);
    };
}