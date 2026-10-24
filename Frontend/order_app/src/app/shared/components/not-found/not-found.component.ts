import { Component } from "@angular/core";
import { RouterLink } from "@angular/router";

/**
 * Route: **  (404 — unknown URL)
 *
 * "Take me home" goes to /app, which runs the permission-based
 * landing logic: authenticated users reach their own home page,
 * everyone else is bounced to /login.
 */
@Component({
    selector: 'app-not-found',
    standalone: true,
    imports: [RouterLink],
    templateUrl: './not-found.component.html'
})
export class NotFoundComponent {
}
