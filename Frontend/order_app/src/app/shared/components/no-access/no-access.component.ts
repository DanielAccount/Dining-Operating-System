import { Component, inject } from "@angular/core";
import { AuthService } from "../../../core/auth/auth.service";

/**
 * Route: /app/no-access
 *
 * Shown to authenticated users whose account has no usable
 * permissions (bad role assignment), or who end up on a page
 * their role cannot see. Kept inside the staff shell so the
 * logout button is always reachable.
 */
@Component({
    selector: 'app-no-access',
    standalone: true,
    templateUrl: './no-access.component.html'
})
export class NoAccessComponent {
    protected authService = inject(AuthService);
}
