import { Component, OnInit, inject } from "@angular/core";
import { Router } from "@angular/router";
import { AuthService } from "../../core/auth/auth.service";

/**
 * Route: /app
 *
 * Single source of truth for "where does this user go" lives in
 * AuthService.landingUrl() — this component just executes it, so
 * hitting /app directly (bookmark, after-login, permission-guard
 * fallback) lands every role on the right page.
 */
@Component({
    selector: 'app-landing',
    standalone: true,
    templateUrl: './landing.html'
})
export class LandingComponent implements OnInit {
    private authService = inject(AuthService);
    private router = inject(Router);

    ngOnInit(): void {
        this.router.navigateByUrl(this.authService.landingUrl());
    }
}
