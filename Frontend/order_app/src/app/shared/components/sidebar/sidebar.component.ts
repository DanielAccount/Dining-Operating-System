import { Component, inject } from "@angular/core";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";
import { HasPermissionDirective } from "../../directives/has_permission_directuve";
import { AuthService } from "../../../core/auth/auth.service";

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [RouterLink, RouterLinkActive, RouterOutlet, HasPermissionDirective],
    templateUrl: './sidebar.component.html'
})
export class SideBarComponent{
    protected authService = inject(AuthService)

    onLogout(): void {
        this.authService.logout();
    }
}