import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { map } from "rxjs"; // <-- Missing import
import { AuthService } from "./auth.service";

export const AuthGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return authService.restoreSession().pipe(
    map((isValid: boolean) => {
      if (isValid) {
        return true;
      }

      return router.createUrlTree(['/login']);
    })
  );
};